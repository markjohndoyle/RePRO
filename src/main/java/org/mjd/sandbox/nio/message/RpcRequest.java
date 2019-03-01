package org.mjd.sandbox.nio.message;

import java.io.Serializable;

public final class RpcRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    // TEMP USE CASE for development - reposnse object are not yet implemented.
    public static final RpcRequest ERROR = new RpcRequest(-1,"error");

    private long id;
    private String method;
//    private ArrayList<Object> args;

    public RpcRequest()
    {
        // TODO Auto-generated constructor stub
    }


//    public RpcRequest(long id, String method)
//    {
//        this.id = id;
//        this.method = method;
//    }

    public RpcRequest(long id, String method)
    {
        this.id = id;
        this.method = method;
    }

    public long getId() { return id;}
    public String getMethod() { return method;}

    public void setId(long id)
    {
        this.id = id;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }


	@Override
	public String toString() {
		return "RpcRequest [id=" + id + ", method=" + method + "]";
	}

}
