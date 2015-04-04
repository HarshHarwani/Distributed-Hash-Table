package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {
    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static Context context;
    private String[] portNumbers = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static final String KEY_FIELD = "key";
    static final String VALUE_FIELD = "value";
    String finalFetchedQuery=null;
    String portStr = null;
    String myPort = null;
    public Uri mUri=null;
    boolean waitflag=true;
    //boolean waitStarFlag=true;
    //(Hash,"originalPort"+"~"+"successor"+"predecessor")
    TreeMap<String,String> tempJoinNodeMap = new TreeMap<String,String>();
    TreeMap<String,String> finalJoinNodeMap = new TreeMap<String,String>();
    String updatedSuccessor="";
    String updatedPredecessor="";

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String selectionQuery= selection.replaceAll("\"","");
        if (selectionQuery.equals("@")){
            deleteAll();
            return 1;
        }
        else if((!selectionQuery.equals("@") && !selectionQuery.equals("*")))
        {
            deleteRecord(selectionQuery);
            return 1;
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean containsRequest(String query)
    {
        boolean flag=false;
        try {
            Log.d("Inside contains Request","Query-->"+query+" "+"Predecessor->="+updatedPredecessor+" "+"client->"+portStr+" "+"Successor->"+updatedSuccessor);
            if(genHash(updatedPredecessor).compareTo(genHash(portStr)) > 0 && (((genHash(query).compareTo(genHash(portStr)) < 0) || (genHash(query).compareTo(genHash(updatedPredecessor)) > 0))))
                flag=true;
            else if (genHash(query).compareTo(genHash(updatedPredecessor)) > 0 && genHash(query).compareTo(genHash(portStr)) <= 0)
                flag=true;
            else if(updatedSuccessor.equals(portStr) && portStr.equals(updatedPredecessor))
                flag=true;
            else
                flag=false;
            Log.d("Inside contains Request",String.valueOf(flag));
            return flag;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return false;
    }
    public boolean insertRecord(String key,String value)
    {
        FileOutputStream outputStream;
        try {
            outputStream = getContext().openFileOutput(key, getContext().MODE_PRIVATE);
            outputStream.write(value.getBytes());
            outputStream.close();
            Log.d("Insert in first condition",key);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "File write failed");
        }
        return false;
    }
    public String queryRecord(String key)
    {
        FileInputStream inputStream = null;
        MatrixCursor matrixCursor = null;
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        try {
            inputStream = getContext().openFileInput(key);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        inputStreamReader = new InputStreamReader(inputStream);
        bufferedReader = new BufferedReader(inputStreamReader);
        String value = null;
        try {
            value = bufferedReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
       return key+"%"+value;
    }
    public int deleteRecord(String key)
    {
        try {
            getContext().deleteFile(key);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    public int deleteAll()
    {
        try {
            for(String str:getContext().fileList()) {
                getContext().deleteFile(str);
            }
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }


    public String getQueryString() {
        FileInputStream inputStream = null;
        MatrixCursor matrixCursor = null;
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        String query = "";
        for (String str : getContext().fileList()) {
            try {
                inputStream = getContext().openFileInput(str);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            inputStreamReader = new InputStreamReader(inputStream);
            bufferedReader = new BufferedReader(inputStreamReader);
            String key = str;
            try {
                String value = bufferedReader.readLine();
                query = query +key +"="+value+"%";
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return query;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String key = (String)values.get("key");
        String value = (String)values.get("value");
        Log.d("InsideInsertMethodKey",key);
        Log.d("InsideInsertMethodValue",value);
            try {
                Log.d("Insertcalled","Insert");
                if(containsRequest(key)) {
                    if (insertRecord(key, value))
                        Log.d("Inserted key successfully",key);
                }
               else {
                    Log.d("Not found",key);
                    Log.d("Passing it to", updatedSuccessor);
                    String portNoSuccessor="";
                     portNoSuccessor=String.valueOf((Integer.parseInt(updatedSuccessor) * 2));
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "forwardMessage", portNoSuccessor, key, value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        Log.v("insert", value.toString());
        return uri;
    }
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    @Override
    public boolean onCreate() {
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
        Log.d(TAG, "--->Inside OnCreateMethod");
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(getContext().TELEPHONY_SERVICE);
        portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {
            updatedSuccessor=portStr;
            updatedPredecessor=portStr;
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
           Executor e = Executors.newFixedThreadPool(10);
            new ServerTask().executeOnExecutor(e, serverSocket);
          // new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "join", myPort);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        FileInputStream inputStream = null;
        MatrixCursor matrixCursor = null;
        BufferedReader bufferedReader = null;
        InputStreamReader inputStreamReader = null;
        Log.d("selection",selection);
        String selectionQuery= selection.replaceAll("\"","");
        Log.d("selectionQuery",selectionQuery);
        if((!selectionQuery.equals("@") && !selectionQuery.equals("*")))
        {
            try {
                if(containsRequest(selectionQuery)) {
                    String fetchedQuery=queryRecord(selectionQuery);
                    String[] result=fetchedQuery.split("%");
                    String key=result[0];
                    String value=result[1];
                    matrixCursor = new MatrixCursor(new String[]{"key", "value"});
                    matrixCursor.newRow().add(key).add(value);
                    return matrixCursor;
                }
                else {
                    Log.d("error point", updatedSuccessor);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "forwardQuery", selectionQuery, myPort);
                    waitflag=true;
                    while (waitflag);
                    Log.d("FinalValueFetchedQueryError",finalFetchedQuery);
                    String[] result=finalFetchedQuery.split("%");
                    String key=result[0];
                    String value=result[1];
                    matrixCursor = new MatrixCursor(new String[]{"key", "value"});
                    matrixCursor.newRow().add(key).add(value);
                    return matrixCursor;

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (selectionQuery.equals("@")) {
            Log.d("Inside Query @ method", "Inside Query @ method");
            matrixCursor = new MatrixCursor(new String[]{"key", "value"});
            for (String str : getContext().fileList()) {
                try {
                    inputStream = getContext().openFileInput(str);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                inputStreamReader = new InputStreamReader(inputStream);
                bufferedReader = new BufferedReader(inputStreamReader);
                Log.d("InsideQuerymethod", "InsideQueryInsidemethod");
                String key = str;
                String value = null;
                try {
                    value = bufferedReader.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                matrixCursor.newRow().add(key).add(value);
            }
            return matrixCursor;
        }
        else if (selectionQuery.equals("*")) {
                matrixCursor = new MatrixCursor(new String[]{"key", "value"});
                String queryStar=getQueryString();
                Log.d("QueryString in *",queryStar);
                Log.d("Inside starquery in Query method" + "queryString-->", queryStar);
                Log.d("Passed * query to successor",updatedSuccessor);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "starQueryMode",portStr,queryStar);
                waitflag=true;
                // waitStarFlag=true;
                Log.d("WaitFlag in StarQuery-->",String.valueOf(waitflag));
                while (waitflag);
                finalFetchedQuery= finalFetchedQuery.substring(0,finalFetchedQuery.length());
                String[] result=finalFetchedQuery.split("%");
                for(int i=0;i<result.length;i++)
                {
                    String[] s = result[i].split("=");
                    String key=s[0];
                    Log.d("key in *",key);
                    String value=s[1];
                    Log.d("key in *",value);
                    matrixCursor.newRow().add(key).add(value);
                }
                Log.d("WaitFlag in StarQuery After while-->",String.valueOf(waitflag));
                //waitStarFlag=true;
                return matrixCursor;
            }
        return null;
        }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket client = null;
            BufferedReader in = null;
            Log.d("ServerAccept","ServerAccept");

            while (true) {
                try {
                    client = serverSocket.accept();
                    if (client.isConnected()) {
                        Log.d("server", "resumed");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Exception occurred in accepting Connection");
                }
                //http://stackoverflow.com/questions/16608878/read-data-from-a-java-socket-Taken the code to read from a socket.
                if (client != null) try {
                    in = new BufferedReader(
                            new InputStreamReader(client.getInputStream()));
                } catch (IOException e) {
                    Log.e(TAG, "Exception occurred in creating Inputstream");
                    e.printStackTrace();
                }
                if (in != null) {
                    String strings = null;
                    try {
                        strings = in.readLine();
                        Log.d("ServerStrings-->",strings);
                        if(strings.contains("join")){
                            //Added join logic to be checked
                            Log.d(TAG,"portStr-->"+portStr);
                            String[] result3=strings.split("~");
                            String port=result3[1];

                            Log.d(TAG,"Inside join condition");
                            try {
                                tempJoinNodeMap.put(genHash(port), port + "~" + updatedSuccessor + "~" + updatedPredecessor);
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                            for(String str:tempJoinNodeMap.keySet())
                            {
                               String[] value=tempJoinNodeMap.get(str).split("~");
                                String portno=value[0];

                                    String succ=getSucc(str);
                                    String pred=getPred(str);
                                    try {
                                           finalJoinNodeMap.put(genHash(portno), portno + "~" + succ + "~" + pred);
                                    } catch (NoSuchAlgorithmException e) {
                                        e.printStackTrace();
                                    }
                            }
                            for(String str :finalJoinNodeMap.keySet())
                            {
                                String[] result=finalJoinNodeMap.get(str).split("~");
                                String portNo=String.valueOf((Integer.parseInt(result[0]) * 2));
                                Log.d("UpdateRequestsent",portNo);
                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"UpdateSuccPre",portNo,result[1],result[2]);
                                Log.d("Port no-->",result[0]);
                                Log.d("Successor-->",result[1]);
                                Log.d("Predecessor-->",result[2]);
                            }
                        }

                        if(strings.contains("forwardMessage"))
                        {
                            Log.d(TAG,"Inside forwardMessage condition");
                            String[] result=strings.split("~");
                            String key=result[1];
                            String value=result[2];
                            Log.d("forwardMessageServer",key);
                            Log.d("forwardMessageServer",value);
                            ContentValues values = new ContentValues();
                            values.put(KEY_FIELD,key);
                            values.put(VALUE_FIELD,value);
                            insert(mUri,values);
                        }
                        if(strings.contains("finalFetchedQuery"))
                        {

                            Log.d("Inside finalFetchedQuery",String.valueOf(waitflag));
                            finalFetchedQuery = strings.split("~")[1];//separate
                            Log.d("Inside finalFetchedQuery",finalFetchedQuery);
                            waitflag=false;
                        }
                        if(strings.contains("starQueryMode"))
                        {
                            // ps.println("starQueryMode"+"~"+originalSenderPort+"~"+queryString);
                            //  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "starQueryMode",myPort,queryString);
                            try {
                                Log.d("InsideStarQueryModeServer", String.valueOf(waitflag));
                                String[] result = strings.split("~");
                                String originalSenderPort = result[1];
                                String queryString="";
                                Log.d("originalSenderPortInServerInStarQueryMode", originalSenderPort);
                                if(result.length>2){
                                queryString = result[2];}
                                else
                                queryString = "";
                                Log.d("QueryString in Server", queryString);
                                Log.d("originalSenderPort-->", originalSenderPort);
                                Log.d("updatedSuccessor-->", updatedSuccessor);


                                if (originalSenderPort.equals(portStr)) {
                                    finalFetchedQuery = queryString;
                                    Log.d("finalFetchedQuery", finalFetchedQuery);
                                    waitflag = false;
                                } else {
                                    String queryForward = queryString + getQueryString();
                                    Log.d("Passed to successor in * in server", updatedSuccessor);
                                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "starQueryMode", originalSenderPort, queryForward);
                                }
                            }
                            catch(ArrayIndexOutOfBoundsException e){
                                e.printStackTrace();
                            }
                        }
                        if(strings.contains("forwardQuery"))
                        {
                            //ps.println("forwardQuery"+"~"+query+"~"+originalSenderPort);
                            Log.d(TAG,"Inside forwardQuery condition");
                            String[] result=strings.split("~");
                            String query=result[1];
                            String originalSenderPort=result[2];
                            if(containsRequest(query))
                            {
                               //send the query back to the sender
                                String finalFetchedQuery=queryRecord(query);
                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "finalFetchedQuery", originalSenderPort, finalFetchedQuery);
                            }
                            else
                            {   //forward to successor
                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "forwardQuery", query,originalSenderPort);
                            }
                            Log.d("ForwardedQueryServer",query);
                            //query(mUri, null, query, null, null);
                        }
                        if(strings.contains("UpdateSuccPre"))
                        {
                            Log.d(TAG,"Inside UpdateSuccPre condition");
                            String[] result=strings.split("~");
                            updatedSuccessor=result[1];
                            updatedPredecessor=result[2];
                            Log.d("updatedSuccessor--->",updatedSuccessor);
                            Log.d("updatedPredecessor--->",updatedPredecessor);
                        }

                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Exception occurred in reading Inputstream");
                    }
                    Log.d(TAG, strings);
                   // publishProgress(strings);
                }
            }
        }
        public String getPred(String key)
        {
            Log.d(TAG,"Inside Predecessor condition");
            String pred="";
            for(String str:tempJoinNodeMap.keySet())
            {
                if(key.equals(tempJoinNodeMap.firstKey()))
                {
                    Map.Entry<String,String> entry=tempJoinNodeMap.lastEntry();
                    String[] result=entry.getValue().split("~");
                    pred=result[0];
                    break;
                }
                else
                {
                    if(key==str){
                        break;
                    }
                    String[] result=tempJoinNodeMap.get(str).split("~");
                    pred=result[0];
                }
            }
            return pred;
        }
        public String getSucc(String key)
        {
            Log.d(TAG,"Inside Successor condition");
            String succ="";
            Set keys = tempJoinNodeMap.keySet();
            for (Iterator i = keys.iterator(); i.hasNext();) {
                String hashKey=(String)i.next();
                try {
                    if(hashKey.equals(tempJoinNodeMap.lastKey()))
                    {
                        Map.Entry<String,String> entrySucc=tempJoinNodeMap.firstEntry();
                        String[] result=entrySucc.getValue().split("~");
                        succ=result[0];
                        break;
                    }
                    else
                    {
                        if(hashKey==key){
                            String hashKeyNext = (String) i.next();
                            String[] resultNext = (String[]) tempJoinNodeMap.get(hashKeyNext).split("~");
                            succ=resultNext[0];
                            break;
                        }
                    }
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            return succ;
        }

    }
    private class ClientTask extends AsyncTask<String, Void, Void> {
        // I used the PrintStream code from :http://www.tutorialspoint.com/javaexamples/net_singleuser.htm
        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String msgToSend = msgs[0];
                String port=msgs[1];

                if(msgToSend.equals("forwardMessage")) {
                    String portClient=msgs[1];
                    String key=msgs[2];
                    String value=msgs[3];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(portClient));
                    if (socket.isConnected()) {
                        Log.d("forwardMessagePort",portClient);
                        Log.d(TAG, "forwardMessage process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println("forwardMessage"+"~"+key+"~"+value);
                        Log.d("forwardMessageKeyClient",key);
                        Log.d("forwardMessageValueClient",value);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }
                if(msgToSend.equals("forwardQuery")) {
                    String query=msgs[1];
                    String originalSenderPort=msgs[2];
                    String successor=String.valueOf((Integer.parseInt(updatedSuccessor) * 2));
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(successor));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println("forwardQuery"+"~"+query+"~"+originalSenderPort);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }
                //  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "starQueryMode",portStr,queryString);
                if(msgToSend.equals("starQueryMode")) {
                    String originalSenderPort=msgs[1];
                    String queryString=msgs[2];
                    Log.d("QueryStringInClientStarQueryMode",queryString);
                    String successor=String.valueOf((Integer.parseInt(updatedSuccessor) * 2));
                    Log.d("Succesor in *",successor);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(successor));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        String newString="starQueryMode"+"~"+originalSenderPort+"~"+queryString;
                        ps.println(newString);
                        Log.d("SentQueryStar-->", newString);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }

                if(msgToSend.equals("finalFetchedQuery")) {
                    String originalSenderPort=msgs[1];
                    String queryResult=msgs[2];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(originalSenderPort));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println("finalFetchedQuery" +"~"+ queryResult);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }

                //As there is Multicast we iterate over all the ports including the one from which the message is sent.
                    Log.d(TAG, msgToSend);
                if(msgToSend.equals("join")) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt("11108"));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started Join");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(msgToSend+"~"+portStr);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }
                if(msgToSend.equals("UpdateSuccPre")) {
                    Log.d("InClientProcess","UpdateSuccPre");
                    String portNoClient=msgs[1];
                    Log.d("portNoClient",portNoClient);
                    String successor=msgs[2];
                    String predecessor=msgs[3];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(portNoClient));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(msgToSend+"~"+successor+"~"+predecessor);
                        Log.d("clientMessage",msgToSend+"~"+successor+"~"+predecessor);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }
              //  new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "QueryStar", updatedSuccessor,stringBuffer.toString());
               /* if(msgToSend.equals("QueryStar")) {
                    Log.d("InClientProcess","QueryStar");
                    String portNoClient=msgs[1];
                    Log.d("portNoClient",portNoClient);
                    String querybuffer=msgs[2];
                    String sender=msgs[3];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(portNoClient));
                    if (socket.isConnected()) {
                        Log.d(TAG, "Client process started");
                        PrintStream ps = new PrintStream
                                (socket.getOutputStream());
                        ps.println(querybuffer+"~"+sender);
                        ps.flush();
                        ps.close();
                        socket.close();
                    }
                }*/
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }

}
