-module(numprocs).
-export([bbench/1, bench/1, bench/2, start/1, start/2, recv/1]).


% create N processes, wait for them to send a msg and die. 
% bench repeats this experiments a few times
bench(N) ->  bench(10, N).


% spawn N processes that block. Measure time taken to spawn. Not
% a good round-trip test.
% bbench repeats this experiments a few times
bbench(N) ->  bbench(10, N).

%======================================================================

bench(0,_) -> done;
bench(M,N) ->
    start(N),
    % Wait a little for possible background cleanup to occur
    receive
	after 1000
	      -> done
	end,
    bench(M-1, N).

bbench(0, _) -> done;
bbench(M, N) -> 
    start(N, block),
    receive 
	after 1000 -> done
	end,
    bbench(M-1, N).

start(N) ->
    statistics(runtime),
    %io:format("spawning ~p procs~n", [N]),
    spawnN(N, self()),
    %io:format("waiting for them to finish~n"),
    wait(N, N).

start(N, block) ->
    statistics(runtime),
    spawnN(N, nil),
    {_, T} = statistics(runtime),
    if T == 0 ->
	    %io:format("Elapsed: ~p ms ~n", [T]);
	    io:format("~p~n", [T]);
       true -> 
	    %io:format("Elapsed: ~p ms, ~p tasks/ms ~n", [T, N/T])
	    io:format("~p~n", [T])
    end.

wait(0, _)  ->    %wait(0, Total) ->
    {_, T} = statistics(runtime),
    if T == 0 ->
	    %io:format("Elapsed: ~p ms ~n", [T]);
	    io:format("~p~n", [T]);
       true -> 
	    %io:format("Elapsed: ~p ms, ~p tasks/ms ~n", [T, Total/T])
	    io:format("~p~n", [T])
    end;
wait(N, Total) ->
    receive 
	done -> 
	     wait(N-1, Total)
    end.


spawnN(0, _) -> done;
spawnN(N, Main) -> 
%    if (N rem 50000 == 0) ->
%	    io:format("#Procs: ~p ~n", [N]);
%       true -> true
%    end,
    spawn(numprocs, recv, [Main]),
    spawnN(N-1, Main).

recv(Main) ->
    if is_pid(Main) -> 
	    Main ! done;
       true ->
	    receive
		hello -> recv(Main)
	    end
    end.
