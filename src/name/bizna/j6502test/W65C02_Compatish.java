package name.bizna.j6502test;

import name.bizna.j6502.AbstractCore;
import name.bizna.j6502.Memory;

/*
 * NOTE: This source file is automatically generated! Manually editing it is
 * pointless!
 */

/**
 * This core strictly emulates a Western Design Center-branded W65C02 or
 * W65C02S, except that some P register behaviors are redefined as on NMOS
 * 6502s, in order to allow Klaus Dormann's 6502 functional tests to pass
 * without modification. As far as I know, there is no real part that acts
 * exactly like this core!
 */
public class W65C02_Compatish extends AbstractCore {
  public W65C02_Compatish(Memory memory) { super(memory); }
  @Override
  @SuppressWarnings("unused")
  protected void executeOpcode(byte opcode) {
    switch(opcode) {
    case (byte)0: {
// implied
memory.readByte(pc);
++pc;
push((byte)(pc>>8));
push((byte)pc);
push((byte)(p|P_B_BIT));
p &= ~P_D_BIT;
p |= P_I_BIT;
pc = memory.readVectorByte(IRQ_VECTOR);
pc |= memory.readVectorByte((short)(IRQ_VECTOR+1))<<8;
    } break;
    case (byte)1: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)2: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
    } break;
    case (byte)3: {
// implied
memory.readByte(pc);
    } break;
    case (byte)4: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
data |= a;
memory.writeByte(ea, data);
    } break;
    case (byte)5: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)6: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)((data&0xFF)<<1);
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)7: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<0);
memory.writeByte(ea, data);
    } break;
    case (byte)8: {
// implied
memory.readByte(pc);
push((byte)(p|P_B_BIT));
    } break;
    case (byte)9: {
// immediate
byte data = memory.readByte(pc);
++pc;
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)10: {
// implied_a
byte data = a;
short result = (short)((data&0xFF)<<1);
data = (byte)result;
simplePUpdateNZC(result);
a = data;
    } break;
    case (byte)11: {
// implied
memory.readByte(pc);
    } break;
    case (byte)12: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
data |= a;
memory.writeByte(ea, data);
    } break;
    case (byte)13: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)14: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)((data&0xFF)<<1);
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)15: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<0)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)16: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_N_BIT) == 0) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)17: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)18: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)19: {
// implied
memory.readByte(pc);
    } break;
    case (byte)20: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
data &= ~a;
memory.writeByte(ea, data);
    } break;
    case (byte)21: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)22: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)((data&0xFF)<<1);
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)23: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<1);
memory.writeByte(ea, data);
    } break;
    case (byte)24: {
// implied
memory.readByte(pc);
p &= ~P_C_BIT;
    } break;
    case (byte)25: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)26: {
// implied_a
byte data = a;
++data;
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)27: {
// implied
memory.readByte(pc);
    } break;
    case (byte)28: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
data &= ~a;
memory.writeByte(ea, data);
    } break;
    case (byte)29: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a |= data;
simplePUpdateNZ(a);
    } break;
    case (byte)30: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)((data&0xFF)<<1);
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)31: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<1)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)32: {
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
push((byte)(pc>>8));
push((byte)pc);
ea |= memory.readByte(pc)<<8;
pc = ea;
    } break;
    case (byte)33: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)34: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
    } break;
    case (byte)35: {
// implied
memory.readByte(pc);
    } break;
    case (byte)36: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
p = (byte)((p&0x3F)|(data&0xC0));
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
    } break;
    case (byte)37: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)38: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)<<1)|(((p&P_C_BIT)!=0)?1:0));
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)39: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<2);
memory.writeByte(ea, data);
    } break;
    case (byte)40: {
// implied
memory.readByte(pc);
p = (byte)(pop() | P_1_BIT);
    } break;
    case (byte)41: {
// immediate
byte data = memory.readByte(pc);
++pc;
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)42: {
// implied_a
byte data = a;
short result = (short)(((data&0xFF)<<1)|(((p&P_C_BIT)!=0)?1:0));
data = (byte)result;
simplePUpdateNZC(result);
a = data;
    } break;
    case (byte)43: {
// implied
memory.readByte(pc);
    } break;
    case (byte)44: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
p = (byte)((p&0x3F)|(data&0xC0));
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
    } break;
    case (byte)45: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)46: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)<<1)|(((p&P_C_BIT)!=0)?1:0));
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)47: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<2)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)48: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_N_BIT) == P_N_BIT) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)49: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)50: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)51: {
// implied
memory.readByte(pc);
    } break;
    case (byte)52: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
p = (byte)((p&0x3F)|(data&0xC0));
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
    } break;
    case (byte)53: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)54: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)<<1)|(((p&P_C_BIT)!=0)?1:0));
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)55: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<3);
memory.writeByte(ea, data);
    } break;
    case (byte)56: {
// implied
memory.readByte(pc);
p |= P_C_BIT;
    } break;
    case (byte)57: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)58: {
// implied_a
byte data = a;
--data;
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)59: {
// implied
memory.readByte(pc);
    } break;
    case (byte)60: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
p = (byte)((p&0x3F)|(data&0xC0));
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
    } break;
    case (byte)61: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a &= data;
simplePUpdateNZ(a);
    } break;
    case (byte)62: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)<<1)|(((p&P_C_BIT)!=0)?1:0));
data = (byte)result;
simplePUpdateNZC(result);
memory.writeByte(ea, data);
    } break;
    case (byte)63: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<3)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)64: {
// implied
memory.readByte(pc);
short ioAddr = pc;
memory.readByte(ioAddr);
p = (byte)(pop() | P_1_BIT);
pc = (short)(pop()&0xFF);
pc |= pop()<<8;
memory.readByte(ioAddr);
    } break;
    case (byte)65: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)66: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
    } break;
    case (byte)67: {
// implied
memory.readByte(pc);
    } break;
    case (byte)68: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
    } break;
    case (byte)69: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)70: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
short result = (short)((data&0xFF)>>1);
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)71: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<4);
memory.writeByte(ea, data);
    } break;
    case (byte)72: {
// implied
memory.readByte(pc);
push(a);
    } break;
    case (byte)73: {
// immediate
byte data = memory.readByte(pc);
++pc;
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)74: {
// implied_a
byte data = a;
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
short result = (short)((data&0xFF)>>1);
data = (byte)result;
simplePUpdateNZ(result);
a = data;
    } break;
    case (byte)75: {
// implied
memory.readByte(pc);
    } break;
    case (byte)76: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
pc = ea;
    } break;
    case (byte)77: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)78: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
short result = (short)((data&0xFF)>>1);
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)79: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<4)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)80: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_V_BIT) == 0) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)81: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)82: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)83: {
// implied
memory.readByte(pc);
    } break;
    case (byte)84: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
    } break;
    case (byte)85: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)86: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
short result = (short)((data&0xFF)>>1);
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)87: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<5);
memory.writeByte(ea, data);
    } break;
    case (byte)88: {
// implied
memory.readByte(pc);
p &= ~P_I_BIT;
    } break;
    case (byte)89: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)90: {
// implied
memory.readByte(pc);
push(y);
    } break;
    case (byte)91: {
// implied
memory.readByte(pc);
    } break;
    case (byte)92: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
    } break;
    case (byte)93: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
a ^= data;
simplePUpdateNZ(a);
    } break;
    case (byte)94: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
short result = (short)((data&0xFF)>>1);
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)95: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<5)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)96: {
// implied
memory.readByte(pc);
short ioAddr = pc;
memory.readByte(ioAddr);
memory.readByte(ioAddr);
pc = (short)(pop()&0xFF);
pc |= pop()<<8;
++pc;
memory.readByte(ioAddr);
    } break;
    case (byte)97: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)98: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
    } break;
    case (byte)99: {
// implied
memory.readByte(pc);
    } break;
    case (byte)100: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
final byte data = 0x00;
memory.writeByte(ea, data);
    } break;
    case (byte)101: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)102: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)>>1)|(((p&P_C_BIT)!=0)?0x80:0));
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)103: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<6);
memory.writeByte(ea, data);
    } break;
    case (byte)104: {
// implied
memory.readByte(pc);
a = pop();
    } break;
    case (byte)105: {
// immediate
byte data = memory.readByte(pc);
++pc;
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)106: {
// implied_a
byte data = a;
short result = (short)(((data&0xFF)>>1)|(((p&P_C_BIT)!=0)?0x80:0));
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
data = (byte)result;
simplePUpdateNZ(result);
a = data;
    } break;
    case (byte)107: {
// implied
memory.readByte(pc);
    } break;
    case (byte)108: {
// absolute_indirect
short ptr = (short)(memory.readByte(pc)&0xFF);
ptr |= memory.readByte((short)(pc+1)) << 8;
memory.readByte((short)(pc+1));
short ea = (short)(memory.readByte(ptr)&0xFF);
ea |= memory.readByte((short)(ptr+1)) << 8;
pc = (short)(pc + 2);
pc = ea;
    } break;
    case (byte)109: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)110: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)>>1)|(((p&P_C_BIT)!=0)?0x80:0));
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)111: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<6)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)112: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_V_BIT) == P_V_BIT) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)113: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)114: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)115: {
// implied
memory.readByte(pc);
    } break;
    case (byte)116: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
final byte data = 0x00;
memory.writeByte(ea, data);
    } break;
    case (byte)117: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)118: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)>>1)|(((p&P_C_BIT)!=0)?0x80:0));
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)119: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data &= ~(1<<7);
memory.writeByte(ea, data);
    } break;
    case (byte)120: {
// implied
memory.readByte(pc);
p |= P_I_BIT;
    } break;
    case (byte)121: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)122: {
// implied
memory.readByte(pc);
y = pop();
    } break;
    case (byte)123: {
// implied
memory.readByte(pc);
    } break;
    case (byte)124: {
// absolute_x_indirect
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
pc = ea;
    } break;
    case (byte)125: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)126: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
short result = (short)(((data&0xFF)>>1)|(((p&P_C_BIT)!=0)?0x80:0));
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
data = (byte)result;
simplePUpdateNZ(result);
memory.writeByte(ea, data);
    } break;
    case (byte)127: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<7)) == 0) {
pc = branchTarget;
}
    } break;
    case (byte)128: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&0) == 0) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)129: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)130: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
    } break;
    case (byte)131: {
// implied
memory.readByte(pc);
    } break;
    case (byte)132: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
final byte data = y;
memory.writeByte(ea, data);
    } break;
    case (byte)133: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)134: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
final byte data = x;
memory.writeByte(ea, data);
    } break;
    case (byte)135: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<0;
memory.writeByte(ea, data);
    } break;
    case (byte)136: {
// implied_y
byte data = y;
--data;
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)137: {
// immediate
byte data = memory.readByte(pc);
++pc;
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
    } break;
    case (byte)138: {
// implied
memory.readByte(pc);
a = x;
simplePUpdateNZ(a);
    } break;
    case (byte)139: {
// implied
memory.readByte(pc);
    } break;
    case (byte)140: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
final byte data = y;
memory.writeByte(ea, data);
    } break;
    case (byte)141: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)142: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
final byte data = x;
memory.writeByte(ea, data);
    } break;
    case (byte)143: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<0)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)144: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_C_BIT) == 0) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)145: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)146: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)147: {
// implied
memory.readByte(pc);
    } break;
    case (byte)148: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
final byte data = y;
memory.writeByte(ea, data);
    } break;
    case (byte)149: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)150: {
// zero_page_y
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + y) & 0xFF);
memory.readByte(base);
++pc;
final byte data = x;
memory.writeByte(ea, data);
    } break;
    case (byte)151: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<1;
memory.writeByte(ea, data);
    } break;
    case (byte)152: {
// implied
memory.readByte(pc);
a = y;
simplePUpdateNZ(a);
    } break;
    case (byte)153: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)154: {
// implied
memory.readByte(pc);
s = x;
    } break;
    case (byte)155: {
// implied
memory.readByte(pc);
    } break;
    case (byte)156: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
final byte data = 0x00;
memory.writeByte(ea, data);
    } break;
    case (byte)157: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
final byte data = a;
memory.writeByte(ea, data);
    } break;
    case (byte)158: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
final byte data = 0x00;
memory.writeByte(ea, data);
    } break;
    case (byte)159: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<1)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)160: {
// immediate
byte data = memory.readByte(pc);
++pc;
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)161: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)162: {
// immediate
byte data = memory.readByte(pc);
++pc;
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)163: {
// implied
memory.readByte(pc);
    } break;
    case (byte)164: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)165: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)166: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)167: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<2;
memory.writeByte(ea, data);
    } break;
    case (byte)168: {
// implied
memory.readByte(pc);
y = a;
simplePUpdateNZ(y);
    } break;
    case (byte)169: {
// immediate
byte data = memory.readByte(pc);
++pc;
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)170: {
// implied
memory.readByte(pc);
x = a;
simplePUpdateNZ(x);
    } break;
    case (byte)171: {
// implied
memory.readByte(pc);
    } break;
    case (byte)172: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)173: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)174: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)175: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<2)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)176: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_C_BIT) == P_C_BIT) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)177: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)178: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)179: {
// implied
memory.readByte(pc);
    } break;
    case (byte)180: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)181: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)182: {
// zero_page_y
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + y) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)183: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<3;
memory.writeByte(ea, data);
    } break;
    case (byte)184: {
// implied
memory.readByte(pc);
p &= ~P_V_BIT;
    } break;
    case (byte)185: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)186: {
// implied
memory.readByte(pc);
x = s;
simplePUpdateNZ(x);
    } break;
    case (byte)187: {
// implied
memory.readByte(pc);
    } break;
    case (byte)188: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)189: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
a = data;
    } break;
    case (byte)190: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)191: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<3)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)192: {
// immediate
byte data = memory.readByte(pc);
++pc;
short val = (short)(((data^0xFF)&0xFF) + (y&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)193: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)194: {
// immediate
byte data = memory.readByte(pc);
++pc;
    } break;
    case (byte)195: {
// implied
memory.readByte(pc);
    } break;
    case (byte)196: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (y&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)197: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)198: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
--data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)199: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<4;
memory.writeByte(ea, data);
    } break;
    case (byte)200: {
// implied_y
byte data = y;
++data;
simplePUpdateNZ(data);
y = data;
    } break;
    case (byte)201: {
// immediate
byte data = memory.readByte(pc);
++pc;
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)202: {
// implied_x
byte data = x;
--data;
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)203: {
// implied
memory.readByte(pc);
state = State.AWAITING_INTERRUPT;
    } break;
    case (byte)204: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (y&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)205: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)206: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
--data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)207: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<4)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)208: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_Z_BIT) == 0) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)209: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)210: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)211: {
// implied
memory.readByte(pc);
    } break;
    case (byte)212: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
    } break;
    case (byte)213: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)214: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
--data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)215: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<5;
memory.writeByte(ea, data);
    } break;
    case (byte)216: {
// implied
memory.readByte(pc);
p &= ~P_D_BIT;
    } break;
    case (byte)217: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)218: {
// implied
memory.readByte(pc);
push(x);
    } break;
    case (byte)219: {
// implied
memory.readByte(pc);
state = State.STOPPED;
    } break;
    case (byte)220: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
    } break;
    case (byte)221: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)222: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
--data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)223: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<5)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)224: {
// immediate
byte data = memory.readByte(pc);
++pc;
short val = (short)(((data^0xFF)&0xFF) + (x&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)225: {
// zero_page_x_indirect
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)226: {
// immediate
byte data = memory.readByte(pc);
++pc;
    } break;
    case (byte)227: {
// implied
memory.readByte(pc);
    } break;
    case (byte)228: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (x&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)229: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)230: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
++data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)231: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<6;
memory.writeByte(ea, data);
    } break;
    case (byte)232: {
// implied_x
byte data = x;
++data;
simplePUpdateNZ(data);
x = data;
    } break;
    case (byte)233: {
// immediate
byte data = memory.readByte(pc);
++pc;
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)234: {
// implied
memory.readByte(pc);
    } break;
    case (byte)235: {
// implied
memory.readByte(pc);
    } break;
    case (byte)236: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (x&0xFF) + 1);
simplePUpdateNZC(val);
    } break;
    case (byte)237: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)238: {
// absolute
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
++data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)239: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<6)) != 0) {
pc = branchTarget;
}
    } break;
    case (byte)240: {
// relative
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
if((p&P_Z_BIT) == P_Z_BIT) {
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
pc = branchTarget;
}
    } break;
    case (byte)241: {
// zero_page_indirect_y
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)242: {
// zero_page_indirect
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)243: {
// implied
memory.readByte(pc);
    } break;
    case (byte)244: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
    } break;
    case (byte)245: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)246: {
// zero_page_x
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
++data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)247: {
// zero_page
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
byte data = memory.readByte(ea);
memory.readByte(ea);
data |= 1<<7;
memory.writeByte(ea, data);
    } break;
    case (byte)248: {
// implied
memory.readByte(pc);
p |= P_D_BIT;
    } break;
    case (byte)249: {
// absolute_y
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)250: {
// implied
memory.readByte(pc);
x = pop();
    } break;
    case (byte)251: {
// implied
memory.readByte(pc);
    } break;
    case (byte)252: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
    } break;
    case (byte)253: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
memory.readByte(ea);
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
    } break;
    case (byte)254: {
// absolute_x
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
byte data = memory.readByte(ea);
memory.readByte(ea);
++data;
simplePUpdateNZ(data);
memory.writeByte(ea, data);
    } break;
    case (byte)255: {
// relative_bit_branch
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
if((data&(1<<7)) != 0) {
pc = branchTarget;
}
    } break;
    }
  }
}
