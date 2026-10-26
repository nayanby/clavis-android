package com.clavis.reader;
import android.app.job.*;import java.util.concurrent.atomic.AtomicBoolean;
public final class CatalogImportService extends JobService {
 AtomicBoolean cancelled;Thread worker;
 @Override public boolean onStartJob(JobParameters parameters){cancelled=new AtomicBoolean();AtomicBoolean run=cancelled;worker=new Thread(()->{try{CatalogDownloads.finishAll(getApplicationContext(),run);}finally{if(!run.get())jobFinished(parameters,false);}},"catalog-import");worker.start();return true;}
 @Override public boolean onStopJob(JobParameters parameters){if(cancelled!=null)cancelled.set(true);if(worker!=null)worker.interrupt();return true;}
}
