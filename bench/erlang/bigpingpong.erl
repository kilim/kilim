-module(bigpingpong).
-export([bench/1, start/1, recv/2]).

% Create N procs. Each proc sends a message to every other (n-1),
% and waits for n-1 msgs from the others, before signalling
% to a collector proc that it is done. Elapsed time is measured.
% N procs => Num Msgs = n(n+1). (Including extra msg initially
% to each proc to start.

bench(N) -> bench(10, N).    % Run benchmark 10 times

bench(0, _) -> done;
bench(M, N) ->
    start(N),
    % Wait a little for possible background cleanup to occur
    receive
	after 1000
	      -> done
	end,
    bench(M-1, N).

start(NumProcesses) ->
    statistics(runtime),
    Pids = spawnN(NumProcesses, NumProcesses, self(), []),
    lists:foreach(
      fun(Pid) ->
	      Pid ! {start, Pids}
      end,
      Pids),
    wait(NumProcesses, NumProcesses).

wait(0, _) ->  %wait(0, OrigN) ->
    {_, T} = statistics(runtime),
%    if T == 0 ->
%	    io:format("Elapsed: ~p ms ~n", [T]);
%       true -> 
%	    io:format("Elapsed: ~p ms, ~p tasks/ms ~n", [T, OrigN/T])
%    end;
    io:format("~p~n", [T]);

wait(N, OrigN) ->
    receive
	done -> 
	     wait(N-1, OrigN)
    end.

    
% Spawn N procs and return list of pids
spawnN(0, _, _, ListAlreadySpawned) -> ListAlreadySpawned;
spawnN(N, OrigN, MainPid, ListAlreadySpawned) -> 
    Pid = spawn(bigpingpong, recv, [MainPid, OrigN]),
    spawnN(N-1, OrigN, MainPid, [Pid|ListAlreadySpawned]).

% Rcv
recv(_, 1) -> done;   % Done after n-1 msgs
recv(MainPid, OrigN) ->
    receive 
	{start, Pids} ->
	    lists:foreach(
	      fun(E) ->
		      if 
			  % Skip self
			  not (E == self()) -> 
			      E ! ping;
			  true -> pass
		      end
	      end,
	      Pids),
	    MainPid ! done,
	    recv(MainPid, OrigN);

	ping ->
	    $ wait for n-1 msgs
	    recv(MainPid, OrigN-1)
    end.
    
			      
		  
