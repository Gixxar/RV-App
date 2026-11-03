package com.poulton.rvapp;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Consistent outline symbols for the four main destinations. */
final class NavigationIcon extends Drawable {
    private final String name;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    NavigationIcon(String name,int color){this.name=name;paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);}
    @Override public void draw(Canvas c){
        int saved=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);path.reset();
        switch(name){
            case "Home":path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5,9);path.lineTo(5,21);path.lineTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);path.lineTo(19,21);path.lineTo(19,9);c.drawPath(path,paint);break;
            case "Map":path.moveTo(12,22);path.cubicTo(8,17,4,12,4,8);path.cubicTo(4,-1,20,-1,20,8);path.cubicTo(20,12,16,17,12,22);c.drawPath(path,paint);c.drawCircle(12,8,3,paint);break;
            case "Checks":c.drawRoundRect(5,4,20,22,2,2,paint);c.drawRoundRect(9,2,16,6,1,1,paint);path.moveTo(8,13);path.lineTo(11,16);path.lineTo(17,10);c.drawPath(path,paint);break;
            default:c.drawRoundRect(3,3,10,10,1,1,paint);c.drawRoundRect(14,3,21,10,1,1,paint);c.drawRoundRect(3,14,10,21,1,1,paint);c.drawRoundRect(14,14,21,21,1,1,paint);break;
        }
        c.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
