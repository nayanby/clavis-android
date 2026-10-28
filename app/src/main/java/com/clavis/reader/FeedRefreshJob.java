package com.clavis.reader;
import android.app.job.*;import java.util.concurrent.atomic.AtomicInteger;
/** Android schedules periodic RSS updates subject to network/battery constraints. */
public final class FeedRefreshJob extends JobService {
 volatile boolean stopped;
 @Override public boolean onStartJob(JobParameters parameters){stopped=false;AtomicInteger remaining=new AtomicInteger(2);Runnable done=()->{if(remaining.decrementAndGet()==0&&!stopped)jobFinished(parameters,false);};FeedRefresh.request(this,"News",null,done);FeedRefresh.request(this,"Journals",null,done);return true;}
 @Override public boolean onStopJob(JobParameters parameters){stopped=true;return true;}
}
