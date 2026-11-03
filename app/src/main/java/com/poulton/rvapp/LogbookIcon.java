package com.poulton.rvapp;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Simple, resolution-independent travel symbols for the dashboard. */
final class LogbookIcon extends Drawable {
    private final Module module;
    private final Path symbol=new Path();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    LogbookIcon(Module module){this.module=module;}
    @Override public void draw(Canvas canvas) {
        symbol.reset();int saved=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/32f,getBounds().height()/32f);
        p.setColor(AdventureStyle.GREEN);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(module) {
            case PLACES:
                Path pin=symbol;pin.moveTo(16,30);pin.cubicTo(10,22,5,16,5,11);pin.cubicTo(5,-1,27,-1,27,11);pin.cubicTo(27,16,22,22,16,30);canvas.drawPath(pin,p);canvas.drawCircle(16,11,4,p);break;
            case ROUTES:
                canvas.drawCircle(7,6,3,p);canvas.drawCircle(25,26,3,p);Path route=symbol;route.moveTo(7,9);route.cubicTo(7,22,25,8,25,23);canvas.drawPath(route,p);break;
            case TRIPS:
                canvas.drawLine(8,3,3,29,p);canvas.drawLine(24,3,29,29,p);canvas.drawLine(16,4,16,10,p);canvas.drawLine(16,15,16,20,p);canvas.drawLine(16,25,16,29,p);break;
            case CHECKS:
                canvas.drawRoundRect(6,4,27,29,2,2,p);canvas.drawLine(11,10,13,12,p);canvas.drawLine(13,12,17,8,p);canvas.drawLine(20,10,23,10,p);canvas.drawLine(11,19,13,21,p);canvas.drawLine(13,21,17,17,p);canvas.drawLine(20,20,23,20,p);break;
            case MAINTENANCE:
                canvas.drawCircle(22,9,6,p);canvas.drawLine(18,13,6,26,p);canvas.drawLine(22,15,10,29,p);canvas.drawLine(6,26,10,29,p);canvas.drawLine(23,3,20,8,p);canvas.drawLine(20,8,24,11,p);break;
            case FUEL:
                canvas.drawRoundRect(5,5,20,28,2,2,p);canvas.drawRect(8,8,17,15,p);canvas.drawLine(3,29,22,29,p);canvas.drawLine(22,10,27,15,p);canvas.drawLine(27,15,27,26,p);canvas.drawLine(27,26,23,26,p);canvas.drawLine(23,26,23,19,p);break;
            case EXPENSES:
                canvas.drawCircle(16,16,13,p);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("serif",Typeface.BOLD));p.setTextSize(24);p.setTextAlign(Paint.Align.CENTER);canvas.drawText("£",16,24,p);break;
            case CAMPSITES:
                canvas.drawLine(3,27,16,5,p);canvas.drawLine(16,5,29,27,p);canvas.drawLine(3,27,29,27,p);canvas.drawLine(16,13,10,27,p);canvas.drawLine(16,13,22,27,p);break;
            case PHOTOS:
                canvas.drawRoundRect(3,8,29,28,3,3,p);canvas.drawCircle(16,18,6,p);canvas.drawLine(9,8,12,4,p);canvas.drawLine(12,4,21,4,p);canvas.drawLine(21,4,24,8,p);break;
            default:
                canvas.drawArc(3,5,29,31,180,180,false,p);canvas.drawLine(3,18,3,27,p);canvas.drawLine(29,18,29,27,p);canvas.drawLine(3,27,29,27,p);canvas.drawLine(16,23,23,12,p);canvas.drawCircle(16,23,2,p);break;
        }
        canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
