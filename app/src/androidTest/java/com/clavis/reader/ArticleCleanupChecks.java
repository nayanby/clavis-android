package com.clavis.reader;
import android.app.*;import android.os.*;
/** Runs publisher extraction regressions without unrelated carousel gesture tests. */
public final class ArticleCleanupChecks extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle output=new Bundle();int status=Activity.RESULT_OK;try{FeedPostChecks.checks=0;ArticleExtractionChecks.run(this);output.putString("stream",FeedPostChecks.checks+" article extraction checks passed");}catch(Throwable error){status=Activity.RESULT_CANCELED;output.putString("stream",android.util.Log.getStackTraceString(error));}finish(status,output);}
}