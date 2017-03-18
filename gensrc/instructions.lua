instructions = {}

setmetatable(instructions,
             {__index=function(t,k) error("Unknown instruction: "..k) end})

function instructions.BRK(flags, am)
   out[[
++pc;
push((byte)(pc>>8));
push((byte)pc);
push((byte)(p|P_B_BIT));
p &= ~P_D_BIT;
p |= P_I_BIT;
pc = (short)(memory.readVectorByte(IRQ_VECTOR)&0xFF);
pc |= memory.readVectorByte((short)(IRQ_VECTOR+1))<<8;
]]
end

function instructions.ORA(flags, am)
   out(am.read)
   out[[
a |= data;
simplePUpdateNZ(a);
]]
end

function instructions.AND(flags, am)
   out(am.read)
   out[[
a &= data;
simplePUpdateNZ(a);
]]
end

function instructions.EOR(flags, am)
   out(am.read)
   out[[
a ^= data;
simplePUpdateNZ(a);
]]
end

function instructions.BIT(flags, am)
   out(am.read)
   out[[
p = (byte)((p&0x3F)|(data&0xC0));
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
]]
end

function instructions.BIT_immediate(flags, am)
   out(am.read)
   out[[
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
]]
end

function instructions.NOP(flags, am)
end

function instructions.TRB(flags, am)
   -- we don't pretend to support multiprocessing, so we can do TSB and TRB
   -- simplistically
   out(am.read)
   out(am.spurious_read)
   out[[
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
data &= ~a;
]]
   out(am.write)
end

function instructions.TSB(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
if((data&a) != 0) p &= ~P_Z_BIT;
else p |= P_Z_BIT;
data |= a;
]]
   out(am.write)
end

function instructions.ASL(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
short result = (short)((data&0xFF)<<1);
data = (byte)result;
simplePUpdateNZC(result);
]]
   out(am.write)
end

function instructions.ROL(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
short result = (short)(((data&0xFF)<<1)|(((p&P_C_BIT)!=0)?1:0));
data = (byte)result;
simplePUpdateNZC(result);
]]
   out(am.write)
end

function instructions.ROR(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
short result = (short)(((data&0xFF)>>1)|(((p&P_C_BIT)!=0)?0x80:0));
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
data = (byte)result;
simplePUpdateNZ(result);
]]
   out(am.write)
end

function instructions.LSR(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
if((data&1)!=0) p |= P_C_BIT;
else p &= ~P_C_BIT;
short result = (short)((data&0xFF)>>1);
data = (byte)result;
simplePUpdateNZ(result);
]]
   out(am.write)
end

function instructions.RMB(flags, am, bit)
   out(am.read)
   out(am.spurious_read)
   out("data &= ~(1<<"..bit..");\n")
   out(am.write)
end

function instructions.SMB(flags, am, bit)
   out(am.read)
   out(am.spurious_read)
   out("data |= 1<<"..bit..";\n")
   out(am.write)
end

function instructions.BBR(flags, am, bit)
   out("if((data&(1<<"..bit..")) == 0) {\n")
   out(am.taking_branch)
   out("pc = branchTarget;\n")
   out("}\n")
end

function instructions.BBS(flags, am, bit)
   out("if((data&(1<<"..bit..")) != 0) {\n")
   out(am.taking_branch)
   out("pc = branchTarget;\n")
   out("}\n")
end

function instructions.Branch(flags, am, mask, cmp)
   out("if((p&"..mask..") == "..cmp..") {\n")
   out(am.taking_branch)
   out("pc = branchTarget;\n")
   out("}\n")
end

function instructions.INC(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
++data;
simplePUpdateNZ(data);
]]
   out(am.write)
end

function instructions.DEC(flags, am)
   out(am.read)
   out(am.spurious_read)
   out[[
--data;
simplePUpdateNZ(data);
]]
   out(am.write)
end

function instructions.JMP(flags, am)
   out("pc = ea;\n")
end

function instructions.ADC(flags, am)
   out(am.read)
   out[[
short val = (short)((data&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
]]
   -- BCD fixup
   out(am.spurious_read)
   out[[
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
]]
end

function instructions.SBC(flags, am)
   out(am.read)
   out[[
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF));
if((p&P_C_BIT)!=0) ++val;
if((p&P_D_BIT)!=0) {
]]
   -- BCD fixup
   out(am.spurious_read)
   out[[
if((val&0x0F) > 0x09) val += 0x06;
if((val&0xF0) > 0x90) val += 0x60;
}
if(((a^val)&~(data^val)&0x80)!=0) p |= P_V_BIT;
else p &= ~P_V_BIT;
simplePUpdateNZC(val);
a = (byte)val;
]]
end

function instructions.CMP(flags, am)
   out(am.read)
   out[[
short val = (short)(((data^0xFF)&0xFF) + (a&0xFF) + 1);
simplePUpdateNZC(val);
]]
end

function instructions.CPX(flags, am)
   out(am.read)
   out[[
short val = (short)(((data^0xFF)&0xFF) + (x&0xFF) + 1);
simplePUpdateNZC(val);
]]
end

function instructions.CPY(flags, am)
   out(am.read)
   out[[
short val = (short)(((data^0xFF)&0xFF) + (y&0xFF) + 1);
simplePUpdateNZC(val);
]]
end

function instructions.STZ(flags, am)
   out[[
final byte data = 0x00;
]]
   out(am.write)
end

function instructions.STA(flags, am)
   out[[
final byte data = a;
]]
   out(am.write)
end

function instructions.STX(flags, am)
   out[[
final byte data = x;
]]
   out(am.write)
end

function instructions.STY(flags, am)
   out[[
final byte data = y;
]]
   out(am.write)
end

function instructions.LDA(flags, am)
   out(am.read)
   out[[
simplePUpdateNZ(data);
a = data;
]]
end

function instructions.LDX(flags, am)
   out(am.read)
   out[[
simplePUpdateNZ(data);
x = data;
]]
end

function instructions.LDY(flags, am)
   out(am.read)
   out[[
simplePUpdateNZ(data);
y = data;
]]
end

function instructions.JSR(flags, am)
   out[[
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
push((byte)(pc>>8));
push((byte)pc);
ea |= memory.readByte(pc)<<8;
pc = ea;
]]
end

function instructions.RTI(flags, am)
   out[[
short ioAddr = pc;
memory.readByte(ioAddr);
]]
   if flags.more_compatible_with_6502 then
      out[[
p = (byte)(pop() | P_1_BIT);
]]
   else
      out[[
p = pop();
]]
   end
   out[[
pc = (short)(pop()&0xFF);
pc |= pop()<<8;
memory.readByte(ioAddr);
]]
end

function instructions.RTS(flags, am)
   out[[
short ioAddr = pc;
memory.readByte(ioAddr);
memory.readByte(ioAddr);
pc = (short)(pop()&0xFF);
pc |= pop()<<8;
++pc;
memory.readByte(ioAddr);
]]
end

function instructions.PHP(flags, am)
   if flags.more_compatible_with_6502 then
      out[[
push((byte)(p|P_B_BIT));
]]
   else
      out [[
push(p);
]]
   end
end

function instructions.PLP(flags, am)
   if flags.more_compatible_with_6502 then
      out[[
p = (byte)(pop() | P_1_BIT);
]]
   else
      out[[
p = pop();
]]
   end
end

function instructions.PHA(flags, am)
   out[[
push(a);
]]
end

function instructions.PLA(flags, am)
   out[[
a = pop();
]]
end

function instructions.PHX(flags, am)
   out[[
push(x);
]]
end

function instructions.PLX(flags, am)
   out[[
x = pop();
]]
end

function instructions.PHY(flags, am)
   out[[
push(y);
]]
end

function instructions.PLY(flags, am)
   out[[
y = pop();
]]
end

function instructions.CLC(flags, am)
   out[[
p &= ~P_C_BIT;
]]
end

function instructions.SEC(flags, am)
   out[[
p |= P_C_BIT;
]]
end

function instructions.CLV(flags, am)
   out[[
p &= ~P_V_BIT;
]]
end

function instructions.SEV(flags, am)
   out[[
p |= P_V_BIT;
]]
end

function instructions.CLD(flags, am)
   out[[
p &= ~P_D_BIT;
]]
end

function instructions.SED(flags, am)
   out[[
p |= P_D_BIT;
]]
end

function instructions.CLI(flags, am)
   out[[
p &= ~P_I_BIT;
]]
end

function instructions.SEI(flags, am)
   out[[
p |= P_I_BIT;
]]
end

function instructions.TAX(flags, am)
   out[[
x = a;
simplePUpdateNZ(x);
]]
end

function instructions.TXA(flags, am)
   out[[
a = x;
simplePUpdateNZ(a);
]]
end

function instructions.TAY(flags, am)
   out[[
y = a;
simplePUpdateNZ(y);
]]
end

function instructions.TYA(flags, am)
   out[[
a = y;
simplePUpdateNZ(a);
]]
end

function instructions.TSX(flags, am)
   out[[
x = s;
simplePUpdateNZ(x);
]]
end

function instructions.TXS(flags, am)
   out[[
s = x;
]]
end

function instructions.WAI(flags, am)
   out[[
state = State.AWAITING_INTERRUPT;
]]
end

function instructions.STP(flags, am)
   out[[
state = State.STOPPED;
]]
end
