package e.kamata.loctrack2;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

public class MyJobService extends JobService {
    public MyJobService(){}

    /*ISSUE intervalについて
    jobserviceの仕様上jobのintervalを15分未満にできない。製品としては問題ないかもだけどテスト時間がかかる。困る。
    dozeモードについて
    端末が充電状態じゃないとき、スクリーンoffから一定時間経過、静止状態を全て満たすとdozeモードに入り、jobscheduerの機能が制限されてしまう。
    しかもdozeモードが浅いdozeと深いdozeの2種類ある。「一定時間」の定義は浅いdozeが5分、深いdozeが30分+αらしい

    TODO doze中？スリープ中？も位置情報の値が更新されるようにする　
    note
    doze中、onPauseが動いてしまい、位置情報の更新が止まる
    -> 15分に1回動くことが確認できたら、位置情報の更新と送信をjobserviceにまとめて入れたら解決しそう
    深いdoZe中は1時間に1回しか復帰しなそう
    浅いdoze中は5,10,15分と復帰の間隔が変わる
    ↑これらの間隔ごとにjobが働くかどうかを検証する 浅Dozeは大丈夫そう
    深Dozeは放置したけどidle-maintenanceに入らない、ただこれがテスト時のツールの仕様の問題なのかがわからない」
        →たぶんmaintenanceに入ったのを確認。これでBGでも動くことの確認はできた。
        問題は常に5分に1回ではなくなったこと。多分端末ごとの環境で復帰時間が違うから微妙。

    HUCK スリープ復帰直後はスクロール位置がスリープ前のいちになっている
    下までスクロールするボタンの実装
    * */


    private static final String PACKAGE_NAME = MainActivity.packagename;
//    private static final String NOTIFICATION_TITLE = MainActivity.packagename;
    private static final String TAG = MyJobService.class.getSimpleName();
    private static final Integer JOB_INTERVAL = 1000*60*15; //15分
    private static final long JOB_FLEX = 1000*60*5; //5分
    private static final DateFormat df = new SimpleDateFormat("yy/MM/dd HH:mm:ss", Locale.JAPAN);

    //Jobschedulerの準備
    private final static ComponentName JOB_SERVICE_NAME = new ComponentName(PACKAGE_NAME,MyJobService.class.getName());
    private final static int JOB_ID = 0x01;
//    private static final int NOTIFICATION_ID = 0x01;

    public static void cancelJobs(Context context){
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        //HACK nullcheck?
        scheduler.cancel(JOB_ID);
        Log.i(TAG,"Job cancelled");
        //scheduler.cancelAll();
    }

    public static void schedule(Context context){
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID,JOB_SERVICE_NAME);
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putInt("id",JOB_ID);
        builder.setPersisted(true); //再起動時にJobを実行継続させるかどうか
        if(Build.VERSION.SDK_INT>=24){
            builder.setPeriodic(JOB_INTERVAL,JOB_FLEX);
        }else{
            builder.setPeriodic(JOB_INTERVAL);
        }
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY); //Jobの実行に必要なネットワーク形態を設定
//        builder.setRequiresDeviceIdle(true); //アイドル動作するか設定、setBackoffCriteriaと共存できない
        builder.setBackoffCriteria(JOB_INTERVAL,JobInfo.BACKOFF_POLICY_LINEAR); //backoffした時にjobを処理するかどうか
        builder.setExtras(persistableBundle);
//        builder.setRequiresDeviceIdle(true); //deviceがidle maintenance windowの時に実行するかどうか
        //HACK null check?
        scheduler.schedule(builder.build());
        Log.i(TAG,"job scheduled at "+df.format(System.currentTimeMillis()));
        Log.i(TAG,"scheduled time: "+JOB_INTERVAL/1000/60.0+" ± "+JOB_FLEX/1000/60.0+"min");
    }

    private JobParameters mParams;
    WriteActivity write = new WriteActivity();
    LocationActivity loc = new LocationActivity();
    protected ArrayList<ArrayList> sendLogs = new ArrayList<>();

    //登録したタイミングで実行される
    //Jobの実処理(メインスレッド)
    @Override
    public boolean onStartJob(JobParameters params){
//        Log.i(TAG,"onStartJob");
        final Long sendTime = System.currentTimeMillis();
        mParams = params;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG,"scheduled job");
                Log.i(TAG,"at "+df.format(sendTime));

                double[] fuseddata = loc.getLocation();
                write.startVolley(fuseddata[0],fuseddata[1]);

                if(mParams != null){
                    Log.i(TAG,"job finished");

                    ArrayList locSendLog = new ArrayList<>();
                    locSendLog.add(fuseddata[0]);
                    locSendLog.add(fuseddata[1]);
                    locSendLog.add(sendTime);
                    sendLogs.add(locSendLog);
                    // jobFinished(JobParameters params, boolean needsReschedule)
                    // params: Job開始時にonStartJob()の引数で受け取ったparamsを指定
                    // needsReschedule:
                    //     true JobInfo.Builder.setBackoffCriteria()で指定したback-off criteriaに従って
                    //          JobをRescheduleする
                    //     false Resucheduleしない
                    jobFinished(mParams, true);
                }
            }
        }).start();

        // 処理が継続している場合はtrueを返す。trueを返した場合は、処理終了時にjobFinished()をコールすること
        // 特に何もしていない場合はfalseを返す。
        return true;
    }

    //要求したJobの実行中に条件を満たさなくなった場合に呼び出される。
    //これが呼び出された場合はjobfinished()を呼び出すべき
    @Override
    public boolean onStopJob(JobParameters params){
        jobFinished(params,true);
        //trueの場合jobをback-off設定値に合わせてrescheduleする
        //returnした場合の処理実行は保証されない
        Log.i(TAG,"on stop job:"+params.getJobId());
        return true;
    }

    public void clearSendLogs(){
        sendLogs.clear();
    }

    public ArrayList<ArrayList> getSendLogs(){
        return sendLogs;
    }


}
