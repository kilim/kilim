-module(taskstart).
-export([start/0, startRound/1, launch/1]).

start() ->
    Reporter = spawn(taskstart, startRound, [10]),
    register(reporter, Reporter).

%---------------------------------------------------------------------
% Reporter
% Spawns a ring in each of n rounds, and waits for the ring
% to signal completion. 
%---------------------------------------------------------------------
startRound(0)->
    erlang:halt();

startRound(N)->
    StartTime = erlang:now(),
    spawn(taskstart, launch, [100000]),
    receive
	EndTime ->
	    io:format("~p ~p~n", [N, timer:now_diff(EndTime, StartTime)])
    end,
    startRound(N-1).

%---------------------------------------------------------------------
%Ring
% Each process m spawns process m-1. The last process sends the
% current time to the reporter.
%---------------------------------------------------------------------

launch(0) ->
    EndTime = erlang:now(),
    whereis(reporter) ! EndTime;

launch(M) ->
    spawn(taskstart, launch, [M-1]).


   
