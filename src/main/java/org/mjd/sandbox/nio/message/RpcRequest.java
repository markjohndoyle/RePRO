package org.mjd.sandbox.nio.message;

import java.io.Serializable;

public class RpcRequest implements Serializable
{
    private static final long serialVersionUID = 1L;
    private long id;
    private String method;
//    private ArrayList<Object> args;
    
    public RpcRequest()
    {
        // TODO Auto-generated constructor stub
    }
    

    public RpcRequest(long id, String method)
    {
        this.id = id;
        this.method = method;
//        this.args = new ArrayList<>();
    }
    
    public long getId() { return id;}
    public String getMethod() { return method;}
//    public List<Object> getArguments() { return args;}

    public void setId(long id)
    {
        this.id = id;
    }
    
    public void setMethod(String method)
    {
        this.method = method;
    }
}
