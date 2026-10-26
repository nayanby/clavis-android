package com.clavis.reader;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatImageView;

/** Fits the complete jacket onto a small book, rather than cropping its artwork. */
final class CatalogBookView extends AppCompatImageView {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Matrix face=new Matrix();
 CatalogBookView(Context context){super(context);setScaleType(ScaleType.FIT_CENTER);}
 private float dp(float value){return value*getResources().getDisplayMetrics().density;}
 private void polygon(Canvas canvas,int color,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();paint.setShader(null);paint.setColor(color);canvas.drawPath(path,paint);}
 @Override protected void onDraw(Canvas canvas){
  Drawable jacket=getDrawable();if(jacket==null||getWidth()==0||getHeight()==0)return;
  int sourceW=jacket.getIntrinsicWidth(),sourceH=jacket.getIntrinsicHeight();if(sourceW<=0||sourceH<=0){sourceW=300;sourceH=420;}
  float ratio=300f/420f,depth=dp(7),spine=dp(6),tilt=dp(3);
  float availableW=Math.max(1,getWidth()-dp(24)-depth-spine),availableH=Math.max(1,getHeight()-dp(20)-depth);
  float width=Math.min(availableW,availableH*ratio),height=width/ratio;
  float left=(getWidth()-width-depth-spine)/2+spine,top=(getHeight()-height-depth)/2,right=left+width,bottom=top+height;
  paint.setShader(new RadialGradient((left+right)/2,bottom+dp(7),width*.62f,new int[]{0x30000000,0x00000000},null,Shader.TileMode.CLAMP));
  canvas.save();canvas.scale(1,.16f,(left+right)/2,bottom+dp(7));canvas.drawOval(left-dp(12),bottom+dp(7)-width*.6f,right+dp(12),bottom+dp(7)+width*.6f,paint);canvas.restore();paint.setShader(null);
  // Back board and the exposed paper on the fore-edge and bottom.
  polygon(canvas,0xff727376,left+depth,top+depth,right+depth,top+tilt+depth,right+depth,bottom+depth,left+depth,bottom-tilt+depth);
  polygon(canvas,0xffe8e3d8,right,top+tilt,right+depth,top+tilt+depth,right+depth,bottom+depth,right,bottom);
  polygon(canvas,0xfff6f1e6,left,bottom-tilt,right,bottom,right+depth,bottom+depth,left+depth,bottom-tilt+depth);
  paint.setColor(0xffc9c3b9);paint.setStrokeWidth(dp(.55f));for(int i=1;i<4;i++){float step=depth*i/4;canvas.drawLine(right+step,top+tilt+step,right+step,bottom+step,paint);canvas.drawLine(left+step,bottom-tilt+step,right+step,bottom+step,paint);}
  polygon(canvas,0xff66656a,left-spine,top+dp(2),left,top,left,bottom-tilt,left-spine,bottom-tilt+dp(2));
  // Map all four image corners to the front board: no center-crop or clipping.
  face.setPolyToPoly(new float[]{0,0,300,0,300,420,0,420},0,new float[]{left,top,right,top+tilt,right,bottom,left,bottom-tilt},0,4);
  int saved=canvas.save();canvas.concat(face);paint.setColor(0xffeee8dc);canvas.drawRect(0,0,300,420,paint);float fit=Math.min(300f/sourceW,420f/sourceH);int artW=Math.round(sourceW*fit),artH=Math.round(sourceH*fit);int artLeft=(300-artW)/2,artTop=(420-artH)/2;jacket.setBounds(artLeft,artTop,artLeft+artW,artTop+artH);jacket.draw(canvas);canvas.restoreToCount(saved);
  paint.setShader(new LinearGradient(left,0,left+dp(5),0,new int[]{0x44000000,0x00ffffff},null,Shader.TileMode.CLAMP));canvas.drawRect(left,top+tilt,left+dp(5),bottom-tilt,paint);paint.setShader(null);
 }
}
