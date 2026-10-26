package com.clavis.reader;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatImageView;
/** Square audiobook jacket with soft corners and complete, undistorted artwork. */
final class CatalogAudioCoverView extends AppCompatImageView {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Path clip=new Path();
 CatalogAudioCoverView(Context context){super(context);setScaleType(ScaleType.FIT_CENTER);}
 @Override protected void onDraw(Canvas canvas){
  Drawable artwork=getDrawable();if(artwork==null)return;float density=getResources().getDisplayMetrics().density;
  float side=Math.max(1,Math.min(getWidth()-12*density,getHeight()-10*density));
  float left=(getWidth()-side)/2,top=(getHeight()-side)/2;RectF bounds=new RectF(left,top,left+side,top+side);
  clip.reset();clip.addRoundRect(bounds,11*density,11*density,Path.Direction.CW);
  int saved=canvas.save();canvas.clipPath(clip);paint.setColor(0xffeee8dc);canvas.drawRect(bounds,paint);
  int w=artwork.getIntrinsicWidth(),h=artwork.getIntrinsicHeight();if(w<=0||h<=0){w=300;h=420;}
  float fit=Math.min(side/w,side/h);int width=Math.round(w*fit),height=Math.round(h*fit),x=Math.round(left+(side-width)/2),y=Math.round(top+(side-height)/2);
  artwork.setBounds(x,y,x+width,y+height);artwork.draw(canvas);canvas.restoreToCount(saved);
 }
}
