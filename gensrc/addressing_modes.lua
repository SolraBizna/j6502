addressing_modes = {}

setmetatable(addressing_modes,
             {__index=function(t,k) error("Unknown addressing mode: "..k) end})

-- We could use metatables, or...
local function inherit_from(parent, child)
   for k,v in pairs(parent) do
      if child[k] == nil then child[k] = v end
   end
   return child
end

local has_ea = {
   read=[[
byte data = memory.readByte(ea);
]],
   write=[[
memory.writeByte(ea, data);
]],
   spurious_read=[[
memory.readByte(ea);
]],
}

-------------------------------------------------------------------------------
-- The addressing modes begin!                                               --
-------------------------------------------------------------------------------

-- Implied
-- Takes an extra cycle to read a non-existent operand byte
addressing_modes.implied = {
   init=[[
memory.readByte(pc);
]],
   spurious_read=[[
]],
}

-- Absolute
addressing_modes.absolute = inherit_from(has_ea, {
   init=[[
short ea = (short)(memory.readByte(pc)&0xFF);
ea |= memory.readByte((short)(pc+1)) << 8;
pc = (short)(pc + 2);
]],
})

-- (Absolute,X)
addressing_modes.absolute_x_indirect = inherit_from(has_ea, {
   init=[[
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
]],
})

-- Absolute,X
addressing_modes.absolute_x = inherit_from(has_ea, {
   init=[[
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
]],
})

-- Absolute,X (when used by a RMW instruction)
addressing_modes.absolute_x_RMW = inherit_from(has_ea, {
   init=[[
short base = (short)(memory.readByte(pc)&0xFF)
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (x&0xFF));
memory.readByte((short)(pc+1));
memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
]],
   spurious_read=[[
memory.readByte((short)(ea+1));
]],
})

-- Absolute,Y
addressing_modes.absolute_y = inherit_from(has_ea, {
   init=[[
short base = (short)(memory.readByte(pc)&0xFF);
base |= memory.readByte((short)(pc+1)) << 8;
short ea = (short)(base + (y&0xFF));
memory.readByte((short)(pc+1));
if((ea>>8) != (base>>8)) memory.readByte((short)((ea&0x00FF)|(base&0xFF00)));
pc = (short)(pc + 2);
]],
})

-- (Absolute)
addressing_modes.absolute_indirect = inherit_from(has_ea, {
   init=[[
short ptr = (short)(memory.readByte(pc)&0xFF);
ptr |= memory.readByte((short)(pc+1)) << 8;
memory.readByte((short)(pc+1));
short ea = (short)(memory.readByte(ptr)&0xFF);
ea |= memory.readByte((short)(ptr+1)) << 8;
pc = (short)(pc + 2);
]],
})

-- Implied A
addressing_modes.implied_a = inherit_from(addressing_modes.implied, {
   init=[[
]],
   read=[[
byte data = a;
]],
   write=[[
a = data;
]],
})

-- Implied X
addressing_modes.implied_x = inherit_from(addressing_modes.implied, {
   init=[[
]],
   read=[[
byte data = x;
]],
   write=[[
x = data;
]],
})

-- Implied Y
addressing_modes.implied_y = inherit_from(addressing_modes.implied, {
   init=[[
]],
   read=[[
byte data = y;
]],
   write=[[
y = data;
]],
})

-- Immediate
addressing_modes.immediate = {
   init=[[
byte data = memory.readByte(pc);
++pc;
]],
   read=[[
]],
   spurious_read=[[
]],
}

-- Relative
addressing_modes.relative = {
   init=[[
byte branchOffset = memory.readByte(pc);
++pc;
short branchTarget = (short)(pc + branchOffset);
]],
   -- This cycle only gets potentially burned if the branch is taken
   taking_branch=[[
if((pc>>8) != (branchTarget>>8)) memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
]],
}

-- Relative Bit Branch
addressing_modes.relative_bit_branch = {
   init=[[
byte data = memory.readByte(memory.readByte(pc));
byte branchOffset = memory.readByte((short)(pc+1));
pc = (short)(pc + 2);
short branchTarget = (short)(pc + branchOffset);
memory.readByte((short)((pc&0xFF00)|(branchTarget&0xFF)));
]],
   taking_branch=[[
]],
}

-- Zero Page
addressing_modes.zero_page = inherit_from(has_ea, {
   init=[[
short ea = (short)(memory.readByte(pc)&0xFF);
++pc;
]],
})

-- (Zero Page,X)
addressing_modes.zero_page_x_indirect = inherit_from(has_ea, {
   init=[[
byte base = memory.readByte(pc);
short indexed = (short)((base + x) & 0xFF);
memory.readByte(base);
short ea = (short)(memory.readByte(indexed)&0xFF);
ea |= memory.readByte((short)((indexed+1)&0xFF)) << 8;
++pc;
]],
})

-- Zero Page,X
addressing_modes.zero_page_x = inherit_from(has_ea, {
   init=[[
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + x) & 0xFF);
memory.readByte(base);
++pc;
]],
})

-- Zero Page,Y
addressing_modes.zero_page_y = inherit_from(has_ea, {
   init=[[
short base = (short)(memory.readByte(pc)&0xFF);
short ea = (short)((base + y) & 0xFF);
memory.readByte(base);
++pc;
]],
})

-- (Zero Page)
addressing_modes.zero_page_indirect = inherit_from(has_ea, {
   init=[[
byte base = memory.readByte(pc);
short ea = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ea |= memory.readByte((short)((base+1)&0xFF)) << 8;
++pc;
]],
})

-- (Zero Page),Y
addressing_modes.zero_page_indirect_y = inherit_from(has_ea, {
   init=[[
byte base = memory.readByte(pc);
short ptr = (short)(memory.readByte((short)(base&0xFF))&0xFF);
ptr |= memory.readByte((short)((base+1)&0xFF)) << 8;
short ea = (short)(ptr + (y&0xFF));
if((ea>>8) != (ptr>>8)) memory.readByte((short)((ea&0x00FF)|(ptr&0xFF00)));
++pc;
]],
})

