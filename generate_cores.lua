#!/usr/bin/env lua5.3

dofile("gensrc/addressing_modes.lua")
dofile("gensrc/instructions.lua")

for k,v in pairs(addressing_modes) do
   v.name = k
   v.init = "// "..v.name.."\n"..v.init
end

local function generate_core(pkg, name, opcodes, flags, comment)
   local sourcefile = assert(io.open("src/"..pkg:gsub("%.","/").."/"..name..".java","wb"))
   function out(...) sourcefile:write(...) end
   out("package "..pkg..";\n")
   out[[

import name.bizna.j6502.AbstractCore;
import name.bizna.j6502.Memory;

/*
 * NOTE: This source file is automatically generated! Manually editing it is
 * pointless!
 */

]]
   out(comment)
   out("public class "..name.." extends AbstractCore {\n")
   out("  public "..name.."(Memory memory) { super(memory); }\n")
   out[[
  @Override
  @SuppressWarnings("unused")
  protected void executeOpcode(byte opcode) {
    switch(opcode) {
]]
   for n=0,255 do
      out("    case (byte)"..n..": {\n")
      if opcodes[n][2] ~= nil then out(opcodes[n][2].init) end
      opcodes[n][1](flags, opcodes[n][2], select(3, table.unpack(opcodes[n])))
      out("    } break;\n")
   end
   out[[
    }
  }
}
]]
   sourcefile:close()
   out = nil
end

local opcodes_w65c02 = dofile("gensrc/opcodes_w65c02.lua")
generate_core("name.bizna.j6502", "W65C02", opcodes_w65c02, {}, [[
/**
 * This core strictly emulates a Western Design Center-branded W65C02 or
 * W65C02S. This includes the \"Rockwell bit extensions\".
 */
]])

local opcodes_w65c02 = dofile("gensrc/opcodes_w65c02.lua")
generate_core("name.bizna.j6502test", "W65C02_Compatish",
              opcodes_w65c02, {more_compatible_with_6502=true}, [[
/**
 * This core strictly emulates a Western Design Center-branded W65C02 or
 * W65C02S, except that some P register behaviors are redefined as on NMOS
 * 6502s, in order to allow Klaus Dormann's 6502 functional tests to pass
 * without modification. As far as I know, there is no real part that acts
 * exactly like this core!
 */
]])

