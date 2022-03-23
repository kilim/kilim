package kilim.http;

import java.util.GregorianCalendar;
import java.util.TimeZone;

public class HttpRequestTimeParser {
    // line 1317 "HttpRequestParser.java"
    private static byte[] init__http_date_actions_0()
    {
        return new byte [] {
                0,    1,    0,    1,    1,    1,    2,    1,    3,    1,    4,    1,
                5,    1,    6,    1,    7,    1,    8,    1,    9,    1,   10,    1,
                11,    1,   12,    1,   13,    1,   14,    1,   15,    1,   16
        };
    }

    private static final byte _http_date_actions[] = init__http_date_actions_0();


    private static short[] init__http_date_key_offsets_0()
    {
        return new short [] {
                0,    0,    5,    6,    7,    9,   18,   20,   21,   22,   25,   28,
                31,   34,   36,   39,   41,   44,   47,   48,   49,   50,   51,   52,
                54,   55,   57,   58,   60,   61,   62,   63,   64,   65,   66,   67,
                70,   74,   83,   85,   86,   87,   90,   93,   96,   99,  101,  104,
                106,  109,  111,  112,  113,  114,  115,  116,  117,  118,  120,  121,
                123,  124,  126,  127,  128,  129,  130,  131,  132,  140,  142,  143,
                144,  146,  147,  148,  149,  150,  151,  153,  154,  156,  157,  159,
                160,  161,  162,  163,  164,  165,  166,  167,  169,  170,  172,  173,
                174,  175,  176,  178
        };
    }

    private static final short _http_date_key_offsets[] = init__http_date_key_offsets_0();


    private static char[] init__http_date_trans_keys_0()
    {
        return new char [] {
                70,   77,   83,   84,   87,  114,  105,   32,   44,   32,   65,   68,
                70,   74,   77,   78,   79,   83,  112,  117,  114,   32,   32,   48,
                57,   32,   48,   57,   32,   48,   57,   58,   48,   57,   48,   57,
                58,   48,   57,   48,   57,   32,   48,   57,   32,   48,   57,  103,
                101,   99,  101,   98,   97,  117,  110,  108,  110,   97,  114,  121,
                111,  118,   99,  116,  101,  112,   32,   32,   48,   57,   32,   45,
                48,   57,   32,   65,   68,   70,   74,   77,   78,   79,   83,  112,
                117,  114,   32,   32,   48,   57,   32,   48,   57,   32,   48,   57,
                58,   48,   57,   48,   57,   58,   48,   57,   48,   57,   32,   48,
                57,   32,   71,   77,   84,  103,  101,   99,  101,   98,   97,  117,
                110,  108,  110,   97,  114,  121,  111,  118,   99,  116,  101,  112,
                65,   68,   70,   74,   77,   78,   79,   83,  112,  117,  114,   45,
                48,   57,  103,  101,   99,  101,   98,   97,  117,  110,  108,  110,
                97,  114,  121,  111,  118,   99,  116,  101,  112,  111,  110,   97,
                117,  116,  104,  117,  117,  101,  101,  100,   48,   57,    0
        };
    }

    private static final char _http_date_trans_keys[] = init__http_date_trans_keys_0();


    private static byte[] init__http_date_single_lengths_0()
    {
        return new byte [] {
                0,    5,    1,    1,    2,    9,    2,    1,    1,    1,    1,    1,
                1,    0,    1,    0,    1,    1,    1,    1,    1,    1,    1,    2,
                1,    2,    1,    2,    1,    1,    1,    1,    1,    1,    1,    1,
                2,    9,    2,    1,    1,    1,    1,    1,    1,    0,    1,    0,
                1,    2,    1,    1,    1,    1,    1,    1,    1,    2,    1,    2,
                1,    2,    1,    1,    1,    1,    1,    1,    8,    2,    1,    1,
                0,    1,    1,    1,    1,    1,    2,    1,    2,    1,    2,    1,
                1,    1,    1,    1,    1,    1,    1,    2,    1,    2,    1,    1,
                1,    1,    0,    0
        };
    }

    private static final byte _http_date_single_lengths[] = init__http_date_single_lengths_0();


    private static byte[] init__http_date_range_lengths_0()
    {
        return new byte [] {
                0,    0,    0,    0,    0,    0,    0,    0,    0,    1,    1,    1,
                1,    1,    1,    1,    1,    1,    0,    0,    0,    0,    0,    0,
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    1,
                1,    0,    0,    0,    0,    1,    1,    1,    1,    1,    1,    1,
                1,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                1,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,    1,    0
        };
    }

    private static final byte _http_date_range_lengths[] = init__http_date_range_lengths_0();


    private static short[] init__http_date_index_offsets_0()
    {
        return new short [] {
                0,    0,    6,    8,   10,   13,   23,   26,   28,   30,   33,   36,
                39,   42,   44,   47,   49,   52,   55,   57,   59,   61,   63,   65,
                68,   70,   73,   75,   78,   80,   82,   84,   86,   88,   90,   92,
                95,   99,  109,  112,  114,  116,  119,  122,  125,  128,  130,  133,
                135,  138,  141,  143,  145,  147,  149,  151,  153,  155,  158,  160,
                163,  165,  168,  170,  172,  174,  176,  178,  180,  189,  192,  194,
                196,  198,  200,  202,  204,  206,  208,  211,  213,  216,  218,  221,
                223,  225,  227,  229,  231,  233,  235,  237,  240,  242,  245,  247,
                249,  251,  253,  255
        };
    }

    private static final short _http_date_index_offsets[] = init__http_date_index_offsets_0();


    private static byte[] init__http_date_trans_targs_0()
    {
        return new byte [] {
                2,   89,   91,   93,   96,    0,    3,    0,    4,    0,    5,   34,
                0,    5,    6,   19,   21,   23,   26,   28,   30,   32,    0,    7,
                18,    0,    8,    0,    9,    0,    9,   10,    0,   11,   10,    0,
                11,   12,    0,   13,   12,    0,   14,    0,   15,   14,    0,   16,
                0,   17,   16,    0,   17,   98,    0,    8,    0,   20,    0,    8,
                0,   22,    0,    8,    0,   24,   25,    0,    8,    0,    8,    8,
                0,   27,    0,    8,    8,    0,   29,    0,    8,    0,   31,    0,
                8,    0,   33,    0,    8,    0,   35,    0,   35,   36,    0,   37,
                68,   36,    0,   37,   38,   53,   55,   57,   60,   62,   64,   66,
                0,   39,   52,    0,   40,    0,   41,    0,   41,   42,    0,   43,
                42,    0,   43,   44,    0,   45,   44,    0,   46,    0,   47,   46,
                0,   48,    0,   49,   48,    0,   49,   50,    0,   51,    0,   99,
                0,   40,    0,   54,    0,   40,    0,   56,    0,   40,    0,   58,
                59,    0,   40,    0,   40,   40,    0,   61,    0,   40,   40,    0,
                63,    0,   40,    0,   65,    0,   40,    0,   67,    0,   40,    0,
                69,   74,   76,   78,   81,   83,   85,   87,    0,   70,   73,    0,
                71,    0,   72,    0,   42,    0,   71,    0,   75,    0,   71,    0,
                77,    0,   71,    0,   79,   80,    0,   71,    0,   71,   71,    0,
                82,    0,   71,   71,    0,   84,    0,   71,    0,   86,    0,   71,
                0,   88,    0,   71,    0,   90,    0,    4,    0,   92,   90,    0,
                4,    0,   94,   95,    0,    4,    0,    4,    0,   97,    0,    4,
                0,   98,    0,    0,    0
        };
    }

    private static final byte _http_date_trans_targs[] = init__http_date_trans_targs_0();


    private static byte[] init__http_date_trans_actions_0()
    {
        return new byte [] {
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,   17,    0,    0,    0,    0,    1,    0,    0,    1,    0,
                0,    5,    0,    0,    5,    0,    7,    0,    0,    7,    0,    9,
                0,    0,    9,    0,    0,    3,    0,   25,    0,    0,    0,   33,
                0,    0,    0,   13,    0,    0,    0,    0,   11,    0,   23,   21,
                0,    0,    0,   15,   19,    0,    0,    0,   31,    0,    0,    0,
                29,    0,    0,    0,   27,    0,    0,    0,    0,    1,    0,    0,
                0,    1,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,    0,    0,   17,    0,    0,    0,    0,    3,    0,    0,
                3,    0,    0,    5,    0,    0,    5,    0,    7,    0,    0,    7,
                0,    9,    0,    0,    9,    0,    0,    0,    0,    0,    0,    0,
                0,   25,    0,    0,    0,   33,    0,    0,    0,   13,    0,    0,
                0,    0,   11,    0,   23,   21,    0,    0,    0,   15,   19,    0,
                0,    0,   31,    0,    0,    0,   29,    0,    0,    0,   27,    0,
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                17,    0,    0,    0,    3,    0,   25,    0,    0,    0,   33,    0,
                0,    0,   13,    0,    0,    0,    0,   11,    0,   23,   21,    0,
                0,    0,   15,   19,    0,    0,    0,   31,    0,    0,    0,   29,
                0,    0,    0,   27,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
                0,    3,    0,    0,    0
        };
    }

    private static final byte _http_date_trans_actions[] = init__http_date_trans_actions_0();



    static final int http_date_start = 1;
    static final int http_date_first_final = 98;
    static final int http_date_error = 0;

    static final int http_date_en_main = 1;


    // line 285 "HttpRequestParser.rl"
    public static TimeZone GMT = TimeZone.getTimeZone("GMT");

    public static long parseDate(byte[] data, int pos, int len) {
        int p = 0;
        int pe = len;
//    int eof = pe;
        int cs;
//    int wkday = 0;
        int day = 0, month = 0, year = 0;
        int hh = 0, mm = 0, ss = 0;


// line 1510 "HttpRequestParser.java"
        {
            cs = http_date_start;
        }

// line 299 "HttpRequestParser.rl"

// line 1517 "HttpRequestParser.java"
        {
            int _klen;
            int _trans = 0;
            int _acts;
            int _nacts;
            int _keys;
            int _goto_targ = 0;

            _goto: while (true) {
                switch ( _goto_targ ) {
                    case 0:
                        if ( p == pe ) {
                            _goto_targ = 4;
                            continue _goto;
                        }
                        if ( cs == 0 ) {
                            _goto_targ = 5;
                            continue _goto;
                        }
                    case 1:
                        _match: do {
                            _keys = _http_date_key_offsets[cs];
                            _trans = _http_date_index_offsets[cs];
                            _klen = _http_date_single_lengths[cs];
                            if ( _klen > 0 ) {
                                int _lower = _keys;
                                int _mid;
                                int _upper = _keys + _klen - 1;
                                while (true) {
                                    if ( _upper < _lower )
                                        break;

                                    _mid = _lower + ((_upper-_lower) >> 1);
                                    if ( data[p] < _http_date_trans_keys[_mid] )
                                        _upper = _mid - 1;
                                    else if ( data[p] > _http_date_trans_keys[_mid] )
                                        _lower = _mid + 1;
                                    else {
                                        _trans += (_mid - _keys);
                                        break _match;
                                    }
                                }
                                _keys += _klen;
                                _trans += _klen;
                            }

                            _klen = _http_date_range_lengths[cs];
                            if ( _klen > 0 ) {
                                int _lower = _keys;
                                int _mid;
                                int _upper = _keys + (_klen<<1) - 2;
                                while (true) {
                                    if ( _upper < _lower )
                                        break;

                                    _mid = _lower + (((_upper-_lower) >> 1) & ~1);
                                    if ( data[p] < _http_date_trans_keys[_mid] )
                                        _upper = _mid - 2;
                                    else if ( data[p] > _http_date_trans_keys[_mid+1] )
                                        _lower = _mid + 2;
                                    else {
                                        _trans += ((_mid - _keys)>>1);
                                        break _match;
                                    }
                                }
                                _trans += _klen;
                            }
                        } while (false);

                        cs = _http_date_trans_targs[_trans];

                        if ( _http_date_trans_actions[_trans] != 0 ) {
                            _acts = _http_date_trans_actions[_trans];
                            _nacts = (int) _http_date_actions[_acts++];
                            while ( _nacts-- > 0 )
                            {
                                switch ( _http_date_actions[_acts++] )
                                {
                                    case 0:
// line 254 "HttpRequestParser.rl"
                                    {day = day * 10 + (data[p] - 48);}
                                    break;
                                    case 1:
// line 255 "HttpRequestParser.rl"
                                    {year = year * 10 + (data[p] - 48);}
                                    break;
                                    case 2:
// line 256 "HttpRequestParser.rl"
                                    {hh = hh * 10 + (data[p] - 48) ;}
                                    break;
                                    case 3:
// line 257 "HttpRequestParser.rl"
                                    {mm = mm * 10 + (data[p] - 48) ;}
                                    break;
                                    case 4:
// line 258 "HttpRequestParser.rl"
                                    {ss = ss * 10 + (data[p] - 48) ;}
                                    break;
                                    case 5:
// line 262 "HttpRequestParser.rl"
                                    { month = 0;}
                                    break;
                                    case 6:
// line 263 "HttpRequestParser.rl"
                                    { month = 1;}
                                    break;
                                    case 7:
// line 264 "HttpRequestParser.rl"
                                    { month = 2;}
                                    break;
                                    case 8:
// line 265 "HttpRequestParser.rl"
                                    { month = 3;}
                                    break;
                                    case 9:
// line 266 "HttpRequestParser.rl"
                                    { month = 4;}
                                    break;
                                    case 10:
// line 267 "HttpRequestParser.rl"
                                    { month = 5;}
                                    break;
                                    case 11:
// line 268 "HttpRequestParser.rl"
                                    { month = 6;}
                                    break;
                                    case 12:
// line 269 "HttpRequestParser.rl"
                                    { month = 7;}
                                    break;
                                    case 13:
// line 270 "HttpRequestParser.rl"
                                    { month = 8;}
                                    break;
                                    case 14:
// line 271 "HttpRequestParser.rl"
                                    { month = 90;}
                                    break;
                                    case 15:
// line 272 "HttpRequestParser.rl"
                                    { month = 10;}
                                    break;
                                    case 16:
// line 273 "HttpRequestParser.rl"
                                    { month = 11;}
                                    break;
// line 1664 "HttpRequestParser.java"
                                }
                            }
                        }

                    case 2:
                        if ( cs == 0 ) {
                            _goto_targ = 5;
                            continue _goto;
                        }
                        if ( ++p != pe ) {
                            _goto_targ = 1;
                            continue _goto;
                        }
                    case 4:
                    case 5:
                }
                break; }
        }

// line 300 "HttpRequestParser.rl"

        if (year < 100) {year += 1900;}

        GregorianCalendar gc = new GregorianCalendar();
        gc.set(year, month, day, hh, mm, ss);
        gc.setTimeZone(GMT);
        return gc.getTimeInMillis();
    }





}
