package com.clavis.reader;
import android.app.DownloadManager;
import android.content.*;
public final class EbookDownloadReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){if(!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()))return;long id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1);if(id>=0&&CatalogDownloads.records(context).optJSONObject(Long.toString(id))!=null){CatalogDownloads.schedule(context);return;}if(id<0||EbookDownloads.records(context).optJSONObject(Long.toString(id))==null)return;PendingResult pending=goAsync();new Thread(()->{try{EbookDownloads.importComplete(context.getApplicationContext(),id);}finally{pending.finish();}},"ebook-import").start();}
}
