package com.aquaquenchers.bms;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;
import android.os.Handler;
import android.os.Looper;

/** HTTPS-only API transport. No financial write queue, no embedded privileged keys. */
public final class Backend {
    public static final class Failure extends Exception {
        public final int status;
        Failure(int status,String message){super(message);this.status=status;}
    }
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final String base,key;
    public volatile String accessToken="",refreshToken="";
    public Backend(String base,String key){this.base=base.replaceAll("/+$","");this.key=key;}
    public void close(){executor.shutdown();}
    public boolean configured(){return base.startsWith("https://")&&!key.trim().isEmpty();}
    public void call(String path,JSONObject body,Consumer<JSONObject> success,Consumer<Failure> failure){ call("POST",path,body,success,failure); }
    public void call(String method,String path,JSONObject body,Consumer<JSONObject> success,Consumer<Failure> failure){
        final JSONObject snapshot;try{snapshot=body==null?null:new JSONObject(body.toString());}catch(Exception e){main.post(()->failure.accept(new Failure(0,"Invalid request. Nothing was saved.")));return;}
        executor.execute(()->{try{JSONObject result=request(method,path,snapshot); main.post(()->success.accept(result));}
            catch(Failure e){main.post(()->failure.accept(e));}});
    }
    private JSONObject request(String method,String path,JSONObject body)throws Failure{
        if(!configured())throw new Failure(0,"The service is not configured. Contact the administrator. Nothing was saved.");
        HttpURLConnection connection=null;
        try{
            URL url=new URL(base+path);
            if(!"https".equals(url.getProtocol()))throw new Failure(0,"A secure HTTPS backend is required.");
            connection=(HttpURLConnection)url.openConnection(); connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestMethod(method);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("apikey",key);connection.setRequestProperty("Authorization","Bearer "+(accessToken.trim().isEmpty()?key:accessToken));connection.setRequestProperty("Content-Type","application/json"); if(!"GET".equals(method))connection.setDoOutput(true);
            if(!"GET".equals(method))try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int status=connection.getResponseCode();InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
            String text="";if(stream!=null){try(InputStream in=stream;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>4*1024*1024)throw new Failure(status,"Response is too large.");bytes.write(b,0,n);}text=bytes.toString(StandardCharsets.UTF_8.name());}}
            JSONObject result;try{result=text.trim().isEmpty()?new JSONObject():new JSONObject(text);}catch(Exception e){throw new Failure(status,"The backend returned an invalid response. Nothing is confirmed saved.");}
            if(status<200||status>=300){String message=result.optString("message",result.optString("error",result.optString("msg","Request failed."))); if(message.length()>300)message="Request rejected. Check your entries and permissions.";throw new Failure(status,message);}
            return result;
        }catch(Failure e){throw e;}catch(Exception e){throw new Failure(0,"Connection failed. Inputs are preserved. The save outcome may be unknown; retry the same request safely when connected.");}
        finally{if(connection!=null)connection.disconnect();}
    }
}
