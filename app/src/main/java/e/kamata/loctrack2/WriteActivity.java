package e.kamata.loctrack2;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.StringRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

//volleyで通信するクラス、android4以上だとこういうので仲介させないとダメらしい
public class WriteActivity extends Activity{
    public void startVolley(final double latitude, final double longitude) {
        new Thread(){
            public void run(){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final MainActivity main = new MainActivity();
                        final Context mContext = main.getInstance().getApplicationContext();
                        File cacheDir = mContext.getCacheDir();
                        DiskBasedCache cache = new DiskBasedCache(cacheDir, 1024 * 1024);
                        BasicNetwork network = new BasicNetwork(new HurlStack());
                        //queue
                        RequestQueue postQueue;
                        postQueue = new RequestQueue(cache, network);
                        //サーバーのアドレス
                        String POST_URL = "http://13.114.93.67/json/edit.php";

                        StringRequest stringReq = new StringRequest(Request.Method.POST, POST_URL,
                                //通信成功
                                new Response.Listener<String>() {
                                    @Override
                                    public void onResponse(String s) {
                                    }
                                },
                                //通信失敗
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError error) {
                                    }
                                }){
                            //送信するデータを設定
                            @Override
                            protected Map<String, String> getParams() {
                                Log.d("StringRequest","Start Requesting to server");
                                Map<String, String> params = new HashMap<>();
                                params.put("latitude", String.valueOf(latitude));
                                params.put("longitude", String.valueOf(longitude));
                                return params;
                            }
                        };
                        postQueue.add(stringReq);
                        postQueue.start();
                    }
                });
            }
        }.start();
    }
}