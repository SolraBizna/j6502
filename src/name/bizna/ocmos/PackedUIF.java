package name.bizna.ocmos;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import li.cil.oc.api.machine.Value;

public class PackedUIF {
	public static final int UIFTAG_STRING = 0x0000;
	public static final int UIFTAG_BYTE_ARRAY = 0x4000;
	public static final int UIFTAG_END = -1;
	public static final int UIFTAG_NULL = -2;
	public static final int UIFTAG_DOUBLE = -4;
	public static final int UIFTAG_INTEGER = -5;
	public static final int UIFTAG_ARRAY = -6;
	public static final int UIFTAG_COMPOUND = -7;
	public static final int UIFTAG_UUID = -8;
	public static final int UIFTAG_TRUE = -9;
	public static final int UIFTAG_FALSE = -10;
	public static final int UIFTAG_VALUE = -32768;
	public static class EndTag {}
	public static EndTag endTag = new EndTag();
	public static void write(DataOutputStream stream, Object o, OCMOS arch) throws IOException {
		if(o instanceof Value) {
			byte val = arch.mapValue((Value)o);
			stream.writeShort((short)(UIFTAG_VALUE + (val&0xFF)));
		}
		else if(o instanceof byte[]) {
			byte[] a = (byte[])o;
			if(a.length > 0x3FFF) throw new IOException("byte array too long");
			stream.writeShort((short)(UIFTAG_BYTE_ARRAY + a.length));
			stream.write(a, 0, a.length);
		}
		else if(o instanceof String) {
			String s = (String)o;
			if(s.length() == 36) {
				try {
					UUID uuid = UUID.fromString(s);
					if(uuid.toString().equals(s)) {
						write(stream, uuid, arch);
						return;
					}
				}
				catch(IllegalArgumentException e) { /* ... carry on */ }
			}
			byte[] a = s.getBytes("UTF-8");
			if(a.length > 0x3FFF) throw new IOException("string too long");
			stream.writeShort((short)(UIFTAG_STRING + a.length));
			stream.write(a, 0, a.length);
		}
		else if(o == null) {
			stream.writeShort((short)UIFTAG_NULL);
		}
		else if(o instanceof Boolean) {
			stream.writeShort((short)(((Boolean)o).booleanValue() ? UIFTAG_TRUE : UIFTAG_FALSE));
		}
		else if(o instanceof Integer) {
			stream.writeShort(UIFTAG_INTEGER);
			stream.writeInt(((Integer)o).intValue());
		}
		else if(o instanceof Number) {
			double d = ((Number)o).doubleValue();
			int i = (int)d;
			if(i == d) {
				stream.writeShort(UIFTAG_INTEGER);
				stream.writeInt(i);
			}
			else {
				stream.writeShort(UIFTAG_DOUBLE);
				stream.writeDouble(d);
			}
		}
		else if(o instanceof Object[]) {
			stream.writeShort((short)UIFTAG_ARRAY);
			for(Object sub : (Object[])o) {
				write(stream, sub, arch);
			}
			write(stream, endTag, arch);
		}
		else if(o instanceof Map<?,?>) {
			Map<?,?> map = (Map<?,?>)o;
			stream.writeShort((short)UIFTAG_COMPOUND);
			for(Object sub : map.keySet()) {
				Map.Entry<?,?> ent = (Map.Entry<?,?>)sub;
				write(stream, ent.getKey(), arch);
				write(stream, ent.getValue(), arch);
			}
			write(stream, endTag, arch);
		}
		else if(o instanceof UUID) {
			UUID u = (UUID)o;
			stream.writeShort((short)UIFTAG_UUID);
			// do not endian swap these
			stream.writeLong(u.getMostSignificantBits());
			stream.writeLong(u.getLeastSignificantBits());
		}
		else if(o instanceof EndTag) {
			stream.writeShort(UIFTAG_END);
		}
		else
			throw new IOException("don't know what that kind of object is");
	}
	public static final Object read(DataInputStream stream, OCMOS arch) throws IOException {
		short tag = stream.readShort();
		switch((int)tag) {
		default:
			if((tag & 0xFFFF) < 0x4000) {
				int len = tag;
				byte[] bytes = new byte[len];
				stream.read(bytes, 0, len);
				String ret = new String(bytes, "UTF-8"); // TODO: use CharsetDecoder and specify handling of invalid UTF-8
				return ret;
			}
			else if((tag & 0xFFFF) < 0x8000) {
				int len = tag-0x4000;
				byte[] ret = new byte[len];
				stream.read(ret, 0, len);
				return ret;
			}
			else if((tag & 0xFFFF) < 0x8100) {
				Value v = arch.getValue((byte)(tag & 0xFF));
				if(v == null) throw new IOException("Invalid Value handle");
				return v;
			}
			else throw new IOException("Invalid UIF tag");
		case UIFTAG_END:
			return endTag;
		case UIFTAG_NULL:
			return null;
		case UIFTAG_DOUBLE:
			return stream.readDouble();
		case UIFTAG_INTEGER:
			return stream.readInt();
		case UIFTAG_ARRAY: {
			ArrayList<Object> ret = new ArrayList<Object>();
			while(true) {
				Object o = read(stream, arch);
				if(o == endTag) break;
				else ret.add(o);
			}
			return ret.toArray();
		}
		case UIFTAG_COMPOUND: {
			Map<Object,Object> ret = new HashMap<Object,Object>();
			while(true) {
				Object k = read(stream, arch);
				if(k == endTag) break;
				if(k == null || k instanceof byte[] || k instanceof Object[] || k instanceof Map)
					throw new IOException("invalid key in UIFTAG_COMPOUND");
				Object v = read(stream, arch);
				if(v == endTag)
					throw new IOException("prematurely terminated UIFTAG_COMPOUND");
				ret.put(k, v);
			}
			return ret;
		}
		case UIFTAG_UUID: {
			return new UUID(stream.readLong(), stream.readLong());
		}
		case UIFTAG_TRUE: return true;
		case UIFTAG_FALSE: return false;
		}
	}
}
