-- Set up n threads in a chain. Each thread retrieves from its MVar and
-- dumps that value on the next MVar

-- This benchmark adapted from Bryan O'Sullivan's blog. The difference is
-- that the threads are each given their own MVar to reduce contention.

-- Compile with ghc -O2 --make Chain.hs
-- Run ./Chain 100000
-- Compare to java kilim.bench.Chain -ntasks 100000 -nmsgs 1 

module Main where

import Control.Applicative
import Control.Concurrent.MVar
import Control.Concurrent
import System.Environment
import Data.Time

main = do
    mv <- newEmptyMVar
    start <- getCurrentTime
    lastmv <- (loop mv =<< read . head <$> getArgs)
    end <- getCurrentTime
    putStrLn $ "creation time: " ++ show (diffUTCTime end start)
    putMVar mv 0
    lastnum <- takeMVar lastmv
    fin <- getCurrentTime
    putStrLn $ "message time: " ++ show (diffUTCTime fin end)
    putStrLn $ "Threads: " ++ show lastnum

loop :: MVar Int -> Int -> IO (MVar Int)
loop mv n | n <= 0 = return mv
          | otherwise = do 
                           nextmv <- newEmptyMVar
                           forkIO $ do
                              m <- takeMVar mv
                              putMVar nextmv $! m+1
                           loop nextmv $! n-1

