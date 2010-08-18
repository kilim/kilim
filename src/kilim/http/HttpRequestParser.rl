/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.http;

/**
 * --- DO NOT EDIT -----
 * HttpRequestParser.java generated from RAGEL (http://www.complang.org/ragel/) from the
 * specification file HttpRequestParser.rl. All changes must be made in the .rl file.
 **/

import java.util.TimeZone;
import java.util.GregorianCalendar;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.URLDecoder;

public class HttpRequestParser {
  public static final Charset UTF8 = Charset.forName("UTF-8");

  %%{
    # A variation of the Ragel grammar from Zed Shaw's mongrel parser. Thanks, Zed.

    machine http_parser;

    action mark {mark = fpc; }

    action start_query {query_start = fpc; }

    action extract_field_name { 
      field_name = kw_lookup(data, mark, fpc);
      if (field_name == null) {// not a known keyword
        field_name = req.extractRange(mark, fpc);
      }
    }

    action extract_value {
      int value = encodeRange(mark, fpc);
      req.addField(field_name, value);
    }

    action request_path {
      req.uriPath = req.extractRange(mark, fpc);
      String s = req.uriPath;
      int len = s.length();
      boolean need_decode;
      // Scan the string to see if the string requires any conversion.
      for (int i = 0; i < len; i++) {
         char c = s.charAt(i);
         if (c == '%' || c > 0x7F) {
           try {
              // TODO: Correct this. URLDecoder is broken for path (upto
              // JDK1.6): it converts'+' to ' ', which should
              // be done only for the query part of the url.
              req.uriPath = URLDecoder.decode(req.uriPath, "UTF-8");
              break;
           } catch (UnsupportedEncodingException ignore){}
         }
      }
    }

    action uri {
      System.out.println("URI::::" + req.extractRange(mark, fpc));
      //req.uri = req.extractRange(mark, fpc);
    }

    action end_query {
      req.queryStringRange = encodeRange(query_start, fpc);
    }

    action fragment { 
      req.uriFragmentRange = encodeRange(mark, fpc);
    }
  
    action version {
      req.versionRange = encodeRange(mark, fpc);
    }

    CRLF = "\r\n";

    # character types
    CTL = (cntrl | 127);
    safe = ("$" | "-" | "_" | ".");
    extra = ("!" | "*" | "'" | "(" | ")" | ",");
    reserved = (";" | "/" | "?" | ":" | "@" | "&" | "=" | "+");
    unsafe = (CTL | " " | "\"" | "#" | "%" | "<" | ">");
    national = any -- (alpha | digit | reserved | extra | safe | unsafe);
    unreserved = (alpha | digit | safe | extra | national);
    escape = ("%" xdigit xdigit);
    uchar = (unreserved | escape);
    pchar = (uchar | ":" | "@" | "&" | "=" | "+");
    tspecials = ("(" | ")" | "<" | ">" | "@" | "," | ";" | ":" | "\\" | "\"" | "/" | "[" | "]" | "?" | "=" | "{" | "}" | " " | "\t");

    # elements
    token = (ascii -- (CTL | tspecials));

    # URI schemes and absolute paths
    scheme = ( alpha | digit | "+" | "-" | "." )* ;
    absolute_uri = (scheme ":" (uchar | reserved )*);
    #path = ( pchar+ ( "/" pchar* )* ) ;
    query = ( uchar | reserved )* %end_query ;
    param = ( pchar | "/" )* ;
    params = ( param ( ";" param )* ) ;
    path  = (any -- [ ?#;])+ %request_path ;
    rel_path = ( path? (";" params)? ) ("?" %start_query query)?;
    absolute_path = ( "/"+ rel_path );
    uri = ( absolute_uri | absolute_path ) >mark ;# %uri;
    fragment = ( uchar | reserved )* >mark %fragment;

    field_name = ( any -- ":" )+ >mark %extract_field_name;
    field_value = any* >mark %extract_value;
    fields = field_name ":" " "* field_value :> CRLF;

    get = 'GET'i  @{req.method = "GET";};
    post = 'POST'i  @{req.method = "POST";};
    delete = 'DELETE'i  @{req.method = "DELETE";};
    head = 'HEAD'i  @{req.method = "HEAD";};
    put = 'PUT'i @{req.method = "PUT";};
    options = 'OPTIONS'i @{req.method = "OPTIONS";};
    method = get | delete | post | put | head | options;

    version = "HTTP/"  ( digit+ "." digit+ )  >mark %version;

    start_line = ( method " "+ uri ("#" fragment){0,1} " "+ version CRLF ) ;

    header = start_line ( fields )* CRLF ;

    main := header %err{err("Malformed Header. Error at " + p + "\n" + new String(data, 0, pe, UTF8));};

  }%%

  %% write data;

  public static void err(String msg) throws IOException{
    throw new IOException(msg);
  }

  public static void initHeader(HttpRequest req, int headerLength) throws IOException {
    ByteBuffer bb = req.buffer;
    /* required variables */
    byte[] data = bb.array();
    int p = 0;
    int pe = headerLength;
//  int eof = pe;
    int cs = 0;

    // variables used by actions in http_req_parser machine above.
    int query_start = 0;
    int mark = 0;
    String field_name = "";

    %% write init;
    %% write exec;
    
    if (cs == http_parser_error) {
      throw new IOException("Malformed HTTP Header. p = " + p +", cs = " + cs);
    }
  }

  /**
   * encode the start pos and length as ints;
   */
  public static int encodeRange(int start, int end) {
    return (start << 16) + end ;
  }

  %%{
    machine http_keywords;

    main := |*
    'Accept'i => { kw = "Accept";};
    'Accept-Charset'i => { kw = "Accept-Charset";};
    'Accept-Encoding'i => { kw = "Accept-Encoding";};
    'Accept-Language'i => { kw = "Accept-Language";};
    'Accept-Ranges'i => { kw = "Accept-Ranges";};
    'Age'i => { kw = "Age";};
    'Allow'i => { kw = "Allow";};
    'Authorization'i => { kw = "Authorization";};
    'Cache-Control'i => { kw = "Cache-Control";};
    'Connection'i => { kw = "Connection";};
    'Content-Encoding'i => { kw = "Content-Encoding";};
    'Content-Language'i => { kw = "Content-Language";};
    'Content-Length'i => { kw = "Content-Length";};
    'Content-Location'i => { kw = "Content-Location";};
    'Content-MD5'i => { kw = "Content-MD5";};
    'Content-Range'i => { kw = "Content-Range";};
    'Content-Type'i => { kw = "Content-Type";};
    'Date'i => { kw = "Date";};
    'ETag'i => { kw = "ETag";};
    'Expect'i => { kw = "Expect";};
    'Expires'i => { kw = "Expires";};
    'From'i => { kw = "From";};
    'Host'i => { kw = "Host";};
    'If-Match'i => { kw = "If-Match";};
    'If-Modified-Since'i => { kw = "If-Modified-Since";};
    'If-None-Match'i => { kw = "If-None-Match";};
    'If-Range'i => { kw = "If-Range";};
    'If-Unmodified-Since'i => { kw = "If-Unmodified-Since";};
    'Last-Modified'i => { kw = "Last-Modified";};
    'Location'i => { kw = "Location";};
    'Max-Forwards'i => { kw = "Max-Forwards";};
    'Pragma'i => { kw = "Pragma";};
    'Proxy-Authenticate'i => { kw = "Proxy-Authenticate";};
    'Proxy-Authorization'i => { kw = "Proxy-Authorization";};
    'Range'i => { kw = "Range";};
    'Referer'i => { kw = "Referer";};
    'Retry-After'i => { kw = "Retry-After";};
    'Server'i => { kw = "Server";};
    'TE'i => { kw = "TE";};
    'Trailer'i => { kw = "Trailer";};
    'Transfer-Encoding'i => { kw = "Transfer-Encoding";};
    'Upgrade'i => { kw = "Upgrade";};
    'User-Agent'i => { kw = "User-Agent";};
    'Vary'i => { kw = "Vary";};
    'Via'i => { kw = "Via";};
    'Warning'i => { kw = "Warning";};
    'WWW-Authenticate'i => { kw = "WWW-Authenticate";};
    *|;

    write data;
  }%%

  @SuppressWarnings("unused")
  public static String kw_lookup(byte[] data, int start, int len) {
//    String req = null;
    int ts, te, act;

//    int wb = 0;
    int p = start;
    int pe = start + len;
    int eof = pe;
    int cs;
    String kw = null;
    %% write init;
    %% write exec;

    return kw;
  }

  %%{
      # Parses strings of the form (from rfc2616)
      # 1. Sun, 06 Nov 1994 08:49:37 GMT  ; RFC 822, updated by RFC 1123
      # 2. Sunday, 06-Nov-94 08:49:37 GMT ; RFC 850, obsoleted by RFC 1036
      # 3. Sun Nov  6 08:49:37 1994       ; ANSI Cs asctime() format

      machine http_date;

      SP = ' '+;
      day = digit+  @{day = day * 10 + (data[fpc] - 48);}; 
      year = digit+ @{year = year * 10 + (data[fpc] - 48);};
      hh = digit+ @{hh = hh * 10 + (data[fpc] - 48) ;}; 
      mm = digit+ @{mm = mm * 10 + (data[fpc] - 48) ;}; 
      ss = digit+ @{ss = ss * 10 + (data[fpc] - 48) ;}; 
      wkday   = "Mon" | "Tue" | "Wed" | "Thu" | "Fri" | "Sat" | "Sun";
      weekday = "Monday" | "Tuesday" | "Wednesday" | "Thursday" | "Friday" | "Saturday" | "Sunday";
      month   = 
        ("Jan" @{ month = 0;}) | 
        ("Feb" @{ month = 1;}) | 
        ("Mar" @{ month = 2;}) | 
        ("Apr" @{ month = 3;}) | 
        ("May" @{ month = 4;}) | 
        ("Jun" @{ month = 5;}) | 
        ("Jul" @{ month = 6;}) | 
        ("Aug" @{ month = 7;}) | 
        ("Sep" @{ month = 8;}) | 
        ("Oct" @{ month = 90;}) | 
        ("Nov" @{ month = 10;}) | 
        ("Dec" @{ month = 11;}) ;

      date1        = day SP month SP year;
      date2        = day "-" month "-" year;
      date3        = month SP  day;
      time         = hh ":" mm ":" ss;
      asctime_date = wkday SP date3 SP time SP year;
      rfc850_date  = wkday "," SP date2 SP time SP "GMT";
      rfc1123_date = wkday "," SP date1 SP time SP "GMT";
      HTTP_date    = rfc1123_date | rfc850_date | asctime_date;
      main := HTTP_date;
      write data;
    }%%

    public static TimeZone GMT = TimeZone.getTimeZone("GMT");

  public static long parseDate(byte[] data, int pos, int len) {
    int p = 0;
    int pe = len;
//    int eof = pe;
    int cs;
//    int wkday = 0;
    int day = 0, month = 0, year = 0;
    int hh = 0, mm = 0, ss = 0;
        
    %%write init;
    %%write exec;

    if (year < 100) {year += 1900;}

    GregorianCalendar gc = new GregorianCalendar();
    gc.set(year, month, day, hh, mm, ss);
    gc.setTimeZone(GMT);
    return gc.getTimeInMillis();
  }


  public static String crlf = "\r\n";
  public static void main(String args[]) throws Exception {
    /// Testing
    String s = 
      "GET /favicon.ico#test HTTP/1.1\r\n" +
      "Host: localhost:7262\r\n" +
      "User-Agent: Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.5; en-US; rv:1.9.0.10) Gecko/2009042315 Firefox/3.0.10 Ubiquity/0.1.5\r\n" +
      "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n" +
      "Accept-Language: en-us,en;q=0.5\r\n" +
      "Accept-Encoding: gzip,deflate\r\n" +
      "Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7\r\n" +
      "Keep-Alive: 300\r\n" +
      "Connection: keep-alive\r\n\r\n";
    System.out.println("Input Request: (" + s.length() + " bytes)");System.out.println(s);
    byte[] data = s.getBytes();
    int len = data.length;
    
    System.out.println("=============================================================");
    HttpRequest req = new HttpRequest();
    req.buffer = ByteBuffer.allocate(2048);
    req.buffer.put(data);
    initHeader(req, len);
    System.out.println(req);
  }
}

