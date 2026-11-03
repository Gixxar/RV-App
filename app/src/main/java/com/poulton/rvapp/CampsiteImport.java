package com.poulton.rvapp;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Bounded, offline import of Google Takeout Saved CSV lists. Never fetches URLs. */
public final class CampsiteImport {
    private CampsiteImport() {}
    static final int MAX_BYTES=10*1024*1024, MAX_ROWS=2000, MAX_FIELD=16000;
    public static final class SavedList {
        public final String name;
        public final List<JSONObject> campsites;
        public final int skipped;
        SavedList(String name,List<JSONObject> campsites,int skipped){this.name=name;this.campsites=Collections.unmodifiableList(campsites);this.skipped=skipped;}
    }
    public static final class Preview {
        public final List<JSONObject> fresh;
        public final int duplicates;
        Preview(List<JSONObject> fresh,int duplicates){this.fresh=fresh;this.duplicates=duplicates;}
    }
    public static List<SavedList> read(InputStream input,String filename)throws IOException {
        byte[] bytes=bounded(input,MAX_BYTES);
        List<SavedList> lists=new ArrayList<>();
        if(bytes.length>=4&&bytes[0]=='P'&&bytes[1]=='K'){
            int entries=0,total=0,csvs=0,rows=0;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
                ZipEntry e;
                while((e=zip.getNextEntry())!=null){
                    if(++entries>500)throw new IOException("Too many files in ZIP (maximum 500). Export only Saved in Google Takeout.");
                    // Drain every entry with the same expansion budget. Nothing is extracted to disk.
                    byte[] body=bounded(zip,20*1024*1024-total);total+=body.length;
                    if(!e.isDirectory()&&e.getName().toLowerCase(Locale.ROOT).endsWith(".csv")){
                        if(++csvs>100)throw new IOException("Too many CSV lists (maximum 100). Choose a single extracted list instead.");
                        SavedList list=csv(body,baseName(e.getName()));rows+=list.campsites.size()+list.skipped;
                        if(rows>MAX_ROWS)throw new IOException("Too many places (maximum 2,000 per import). Choose a single extracted list instead.");
                        lists.add(list);
                    }
                    zip.closeEntry();
                }
            }
            if(lists.isEmpty())throw new IOException("No CSV lists in this ZIP. Export Saved from Google Takeout, or choose an extracted CSV list.");
        }else lists.add(csv(bytes,baseName(filename)));
        return lists;
    }
    private static String baseName(String value){
        String name=value==null?"Saved places":value.replace('\\','/');int slash=name.lastIndexOf('/');name=name.substring(slash+1);
        if(name.toLowerCase(Locale.ROOT).endsWith(".csv"))name=name.substring(0,name.length()-4);
        return name.isEmpty()?"Saved places":name;
    }
    private static byte[] bounded(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Import too large: maximum 10 MB file / 20 MB expanded ZIP.");out.write(b,0,n);}
        return out.toByteArray();
    }
    private static SavedList csv(byte[] bytes,String name)throws IOException{
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){throw new IOException("Use a UTF-8 CSV file from Google Takeout.");}
        if(text.startsWith("\ufeff"))text=text.substring(1);
        List<List<String>> rows=parseCsv(text);
        if(rows.isEmpty())throw new IOException("This CSV is empty.");
        Map<String,Integer> headers=new HashMap<>();
        for(int i=0;i<rows.get(0).size();i++){
            String key=rows.get(0).get(i).trim().toLowerCase(Locale.ROOT);
            if(headers.put(key,i)!=null)throw new IOException("Duplicate CSV heading: "+key);
        }
        if(!headers.containsKey("title")||!headers.containsKey("url"))throw new IOException("Choose a Google Takeout Saved CSV with Title and URL columns. Shared-list links, HTML and backup JSON are not supported here.");
        List<JSONObject> records=new ArrayList<>();int skipped=0;
        for(int i=1;i<rows.size();i++){
            List<String> row=rows.get(i);
            if(row.size()!=rows.get(0).size())throw new IOException("CSV row "+(i+1)+" has a different number of columns. No campsites were imported.");
            String title=value(row,headers,"title"),url=mapsUrl(value(row,headers,"url"));
            if(title.isEmpty()||url==null){skipped++;continue;}
            StringBuilder notes=new StringBuilder();
            for(String field:new String[]{"note","comment","tags"}){
                String v=value(row,headers,field);if(!v.isEmpty()){if(notes.length()>0)notes.append("\n\n");if(field.equals("tags"))notes.append("Tags: ");notes.append(v);}
            }
            if(notes.length()>0)notes.append("\n\n");notes.append("Google Maps: ").append(url).append("\nImported list: ").append(name);
            try{
                JSONObject record=new JSONObject().put("Name",title).put("Notes",notes.toString());
                String address=value(row,headers,"address");if(!address.isEmpty())record.put("Address",address);
                records.add(record);
            }catch(JSONException e){throw new IOException("Could not read this campsite.",e);}
        }
        return new SavedList(name,records,skipped);
    }
    private static String value(List<String> row,Map<String,Integer> headers,String key){Integer i=headers.get(key);return i==null?"":row.get(i).trim();}
    /** RFC 4180 quoting, CRLF and embedded newlines; fail closed on broken quoting. */
    private static List<List<String>> parseCsv(String text)throws IOException{
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<=text.length();i++){
            char c=i==text.length()?'\n':text.charAt(i);
            if(quoted){
                if(i==text.length())throw new IOException("Unclosed quote in CSV. No campsites were imported.");
                if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}
                else field.append(c);
            }else if(c==','||c=='\n'||c=='\r'){
                row.add(field.toString());field.setLength(0);closed=false;
                if(row.size()>64)throw new IOException("Too many CSV columns (maximum 64).");
                if(c!=','){
                    if(row.stream().anyMatch(v->!v.trim().isEmpty()))rows.add(row);
                    if(rows.size()>MAX_ROWS+1)throw new IOException("Too many places (maximum 2,000 per import).");
                    row=new ArrayList<>();if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;
                }
            }else if(c=='"'){
                if(field.length()!=0||closed)throw new IOException("Invalid quote in CSV. No campsites were imported.");quoted=true;
            }else{
                if(closed)throw new IOException("Unexpected text after CSV quote. No campsites were imported.");
                if(c=='\u0000')throw new IOException("Invalid CSV encoding.");field.append(c);
            }
            if(field.length()>MAX_FIELD)throw new IOException("A CSV field is too long (maximum 16,000 characters).");
        }
        return rows;
    }
    /** Accept only Google Maps place links; never executes arbitrary imported schemes. */
    public static String mapsUrl(String value){
        try{
            URI uri=new URI(value.trim());String host=uri.getHost(),path=uri.getPath();
            if(host==null||path==null||uri.getUserInfo()!=null||uri.getPort()!=-1||!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme())))return null;
            host=host.toLowerCase(Locale.ROOT);
            boolean google=host.matches("(?:www\\.|maps\\.)?google\\.(?:com|[a-z]{2}|co\\.[a-z]{2}|com\\.[a-z]{2})");
            boolean map=google&&(host.startsWith("maps.")||path.equals("/maps")||path.startsWith("/maps/"));
            boolean shortLink=host.equals("maps.app.goo.gl")&&!path.equals("/")||host.equals("goo.gl")&&path.startsWith("/maps/");
            if(!map&&!shortLink)return null;
            return "https://"+host+(uri.getRawPath()==null?"":uri.getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
        }catch(Exception e){return null;}
    }
    private static final Pattern LINK=Pattern.compile("(?m)^Google Maps: (https?://\\S+)");
    public static String link(JSONObject record){Matcher match=LINK.matcher(record.optString("Notes"));String result=null;while(match.find()){String url=mapsUrl(match.group(1));if(url!=null)result=url;}return result;}
    private static String key(JSONObject record){String link=link(record);return link==null?null:link;}
    public static Preview preview(List<JSONObject> candidates,LogStore store){
        Set<String> known=new HashSet<>();for(LogStore.Entry e:store.list(Module.CAMPSITES)){String key=key(e.data);if(key!=null)known.add(key);}
        List<JSONObject> fresh=new ArrayList<>();int duplicates=0;
        for(JSONObject record:candidates){String key=key(record);if(key!=null&&!known.add(key))duplicates++;else fresh.add(record);}
        return new Preview(fresh,duplicates);
    }
    /** Recheck duplicates inside the transaction; never updates or removes existing records. */
    public static int save(LogStore store,List<JSONObject> candidates)throws JSONException{
        for(JSONObject record:candidates){
            Map<String,String> values=new HashMap<>();for(String field:Module.CAMPSITES.fields)values.put(field,record.optString(field));
            String error=Rules.validateRecord(Module.CAMPSITES,values);if(error!=null)throw new JSONException(error);
            if(link(record)==null)throw new JSONException("Missing Google Maps link");
        }
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{
            List<JSONObject> fresh=preview(candidates,store).fresh;
            // Reverse to keep the source's order in the app's newest-first list.
            for(int i=fresh.size()-1;i>=0;i--)store.save(Module.CAMPSITES,0,fresh.get(i));
            db.setTransactionSuccessful();return fresh.size();
        }finally{db.endTransaction();}
    }
}
