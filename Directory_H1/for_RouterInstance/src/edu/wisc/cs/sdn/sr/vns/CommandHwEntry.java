package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandHwEntry
{
	public static final int HW_INTERFACE = 1;
	public static final int HW_SPEED = 2;
	public static final int HW_SUBNET = 4;
	public static final int HW_IN_USE = 8;
	public static final int HW_FIXED_IP = 16;
	public static final int HW_ETHER = 32;
	public static final int HW_ETH_IP = 64;
	public static final int HW_MASK = 128;
	
	protected int mKey;
	protected byte [] value;
	
	protected CommandHwEntry deserialize(ByteBuffer buf)
	{
		this.mKey = buf.getInt();
		
		this.value = new byte[32];
		buf.get(this.value);
		
		return this;
	}
}
