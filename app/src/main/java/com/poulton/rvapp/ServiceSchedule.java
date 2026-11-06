package com.poulton.rvapp;

import org.json.JSONObject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** User-defined intervals; never invents manufacturer service requirements. */
final class ServiceSchedule {
    static final String[] CATEGORIES={"Engine","Transmission","Generator","Chassis","Tyres & brakes","Habitation","Other"};
    static final String[] SUGGESTIONS={"Engine oil & filter","Fuel filters","Air filter","Coolant service","Transmission service","Generator oil & filter","Chassis lubrication","Tyre inspection","Brake inspection","Habitation inspection"};
    private ServiceSchedule(){}
    static void calculate(JSONObject data,boolean replace){
        String date=data.optString("Last service date"),miles=data.optString("Last service miles");
        int months=data.optInt("Repeat months",0);double interval=data.optDouble("Repeat miles",0);
        if(months>0&&!date.isEmpty()&&(replace||data.optString("Due date").isEmpty()))TripWorkflow.put(data,"Due date",LocalDate.parse(date).plusMonths(months).toString());
        if(interval>0&&!miles.isEmpty()&&(replace||data.optString("Due miles").isEmpty()))TripWorkflow.put(data,"Due miles",String.valueOf(Double.parseDouble(miles)+interval));
    }
    static int state(JSONObject d,double miles,LocalDate today){
        if(d.optBoolean("done"))return 4;
        String date=d.optString("Due date"),reading=d.optString("Due miles");
        if((!date.isEmpty()&&LocalDate.parse(date).isBefore(today))||(!reading.isEmpty()&&Double.parseDouble(reading)<miles))return 0;
        if(Rules.due(date,reading,miles,today))return 1;
        if((!date.isEmpty()&&ChronoUnit.DAYS.between(today,LocalDate.parse(date))<=30)||(!reading.isEmpty()&&Double.parseDouble(reading)-miles<=500))return 2;
        return 3;
    }
    static String status(JSONObject d,double miles,LocalDate today){return new String[]{"Overdue","Due now","Due soon","Scheduled","Completed"}[state(d,miles,today)];}
    static String remaining(JSONObject d,double miles,LocalDate today){
        ArrayList<String> parts=new ArrayList<>();String date=d.optString("Due date"),reading=d.optString("Due miles");
        if(!date.isEmpty()){long days=ChronoUnit.DAYS.between(today,LocalDate.parse(date));parts.add(days==0?"Today":Math.abs(days)+(days<0?" days overdue":" days remaining"));}
        if(!reading.isEmpty()){double left=Double.parseDouble(reading)-miles;parts.add(String.format(Locale.UK,"%,.0f miles %s",Math.abs(left),left<0?"overdue":left==0?"due now":"remaining"));}
        return String.join(" · ",parts);
    }
    static List<LogStore.Entry> ordered(LogStore store){
        List<LogStore.Entry> rows=new ArrayList<>(store.list(Module.REMINDERS));double miles=store.latestMileage();LocalDate today=LocalDate.now();
        rows.sort(Comparator.comparingInt((LogStore.Entry e)->state(e.data,miles,today))
            .thenComparing(e->e.data.optString("Due date").isEmpty()?"9999-12-31":e.data.optString("Due date"))
            .thenComparingDouble(e->e.data.optString("Due miles").isEmpty()?Double.MAX_VALUE:e.data.optDouble("Due miles"))
            .thenComparing(e->e.data.optString("Service")));
        return rows;
    }
}
