package za.ac.uct.cs.powerqope.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import za.ac.uct.cs.powerqope.Config;
import za.ac.uct.cs.powerqope.Logger;

public class DataClient implements Runnable {
    private String collectedResult;
    private String resultDataType;
    public DataClient(String result, String type){
        collectedResult=result;
        resultDataType=type;
    }

    @Override
    public void run() {
       try{
        Socket serverSocket = new Socket(Util.resolveServer(), Config.SERVER_PORT);
        PrintWriter out=new PrintWriter(serverSocket.getOutputStream(),true);
        out.println(collectedResult);
        Logger.i(resultDataType + " Data Sent to Server");
       }
       catch (UnknownHostException e){
           e.printStackTrace();
       }
       catch (IOException e){
           e.printStackTrace();
       }
    }
}
