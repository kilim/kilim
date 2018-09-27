-module(pingpong).
-export([start/0, ping/3, pong/0]).

start() ->
    Pong = spawn(pingpong, pong, []),
    spawn(pingpong, ping, [Pong, erlang:now(), 100000]).

ping(_, StartTime, 0) ->
    io:format("Elapsed: ~p~n", [timer:now_diff(erlang:now(), StartTime)]);

ping(Pong, StartTime, N) ->
    %io:format("ping ~p~n", [N]),
    Pong ! {ping, self()},
    receive 
	pong ->
	    ping(Pong, StartTime, N-1)
    end.


pong() ->
    receive
	{ping, Ping} -> 
	    %io:format("Received ping~n"),
	    Ping ! pong,
	    pong()
    end.
	     
