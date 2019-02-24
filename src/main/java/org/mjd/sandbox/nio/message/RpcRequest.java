package org.mjd.sandbox.nio.message;

import java.io.Serializable;

public final class RpcRequest implements Serializable
{
    private static final long serialVersionUID = 1L;
    private String id;
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

    public RpcRequest(String id, String method)
    {
        this.id = id;
        this.method = method;
    }

    public String getId() { return id;}
    public String getMethod() { return method;}

    public void setId(String id)
    {
        this.id = id;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }
}
