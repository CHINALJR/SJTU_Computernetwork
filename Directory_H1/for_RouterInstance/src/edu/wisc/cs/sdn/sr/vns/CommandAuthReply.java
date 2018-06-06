package edu.wisc.cs.sdn.sr.vns;

import java.nio.ByteBuffer;

public class CommandAuthReply extends Command
{
	protected int usernameLen;
	protected String username;
	protected int[] key;
	
	public CommandAuthReply()
	{ super(Command.VNS_AUTH_REPLY); }
	
	protected CommandAuthReply deserialize(ByteBuffer buf)
	{
		super.deserialize(buf);
		
		this.usernameLen = buf.getInt();
		
		byte[] tmpBytes = new byte[this.usernameLen];
		buf.get(tmpBytes);
		this.username = new String(tmpBytes);
						
		return this;
	}
	
	protected int getSize()
	{ return super.getSize() + 4 + usernameLen + 4*5; }
	
	protected byte[] serialize()
	{
		byte[] data = new byte[this.getSize()];
        ByteBuffer bb = ByteBuffer.wrap(data);
        this.mLen = this.getSize();
        
        byte[] parentData = super.serialize();

        bb.put(parentData);
        bb.putInt(this.usernameLen);
        bb.put(this.username.getBytes());
        for (int i = 0; i < 5; i++)
        { bb.putInt(key[i]); }
        
        return data;
	}
}
