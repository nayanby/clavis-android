package com.clavis.reader;
import android.graphics.*;
import android.widget.TextView;
/** Vector playback glyphs with consistent geometry at every display density. */
// Player screens use the platform Activity/theme; AppCompat is scoped to notebooks.
@android.annotation.SuppressLint("AppCompatCustomView")
final class PlayerButton extends TextView {
 private boolean playbackActive;
 void setPlaying(boolean active){playbackActive=active;setContentDescription(active?"Pause":"Play");invalidate();}
 final String kind; final Paint p=new Paint(3);
 PlayerButton(MainActivity a,String kind,Runnable action){super(a);this.kind=kind;setGravity(17);setText(kind.equals("play")?"▶":"30");setContentDescription(kind);setOnClickListener(v->action.run());setMinHeight(a.dp(48));setFocusable(true);}
 protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,x=getWidth()/2f,y=getHeight()/2f;int color=AppTheme.dark?Color.WHITE:MainActivity.INK;p.setColor(color);p.setStyle(Paint.Style.FILL);c.save();c.translate(x,y);
 if(kind.equals("play")){float r=Math.min(getWidth(),getHeight())*.46f;p.setColor(AppTheme.text);c.drawCircle(0,0,r,p);p.setColor(AppTheme.surface);if(playbackActive){c.drawRoundRect(-9*d,-10*d,-3*d,10*d,1*d,1*d,p);c.drawRoundRect(3*d,-10*d,9*d,10*d,1*d,1*d,p);}else{Path t=new Path();t.moveTo(-6*d,-11*d);t.lineTo(12*d,0);t.lineTo(-6*d,11*d);t.close();c.drawPath(t,p);}}
 else if(kind.equals("back")||kind.equals("forward")){boolean back=kind.equals("back");c.save();if(back)c.scale(-1,1);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.6f*d);p.setStrokeCap(Paint.Cap.ROUND);c.drawArc(-18*d,-18*d,18*d,18*d,-65,300,false,p);p.setStyle(Paint.Style.FILL);Path arrow=new Path();arrow.moveTo(7*d,-21*d);arrow.lineTo(17*d,-12*d);arrow.lineTo(4*d,-12*d);arrow.close();c.drawPath(arrow,p);c.restore();p.setTextSize(13*d);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);c.drawText(getText().toString(),0,5*d,p);}
 else{if(kind.equals("previous"))c.scale(-1,1);Path t=new Path();t.moveTo(-8*d,-8*d);t.lineTo(5*d,0);t.lineTo(-8*d,8*d);t.close();c.drawPath(t,p);c.drawRoundRect(7*d,-9*d,10*d,9*d,1*d,1*d,p);}c.restore();}
}

