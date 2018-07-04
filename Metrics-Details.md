 # Metrics Description
 1. **qcur** : current `queued requests`. For the backend this reports the number queued without a server assigned.
 2. **qmax** : max value of `qcur`
 3. **scur** : current sessions
 4. **smax** : max sessions
 5. **slim** : configured session limit
 6. **stot** : cumulative number of sessions
 7. **bin** : bytes in
 8. **bout** : bytes out
 9. **dreq** : requests denied because of security concerns.
 ```
     - For tcp this is because of a matched tcp-request content rule.
     - For http this is because of a matched http-request or tarpit rule.
```
 10. **dresp** : responses denied because of security concerns.
 ```
     - For http this is because of a matched http-request rule, or "option checkcache".
 ```
 11. **ereq** : request errors. Some of the possible causes are:
 ```
     - early termination from the client, before the request has been sent.
     - read error from the client
     - client timeout
     - client closed connection
     - various bad requests from the client.
     - request was tarpitted.
```
 12. **econ** : number of requests that encountered an error trying to connect to a backend server. The backend stat is the sum of the stat
         for all servers of that backend, plus any connection errors not associated with a particular server (such as the backend having no
         active servers).
 13. **eresp** : response errors. `srv_abrt` will be counted here also.
     Some other errors are:
```
     - write error on the client socket (won't be counted for the server stat)
     - failure applying filters to the response.
```
 14. **wretr** : number of times a connection to a server was retried.
 15. **wredis** : number of times a request was redispatched to another server. The server value counts the number of times that server was
     switched away from.
 16. **status** : status `(UP/DOWN/NOLB/MAINT/MAINT(via)/MAINT(resolution)...)`
 17. **weight** : total weight (backend), server weight (server)
 18. **act** : number of active servers (backend), server is active (server)
 19. **bck** : number of backup servers (backend), server is backup (server)
 20. **chkfail** : number of failed checks. (Only counts checks failed when the server is up.)
 21. **chkdown** : number of `UP->DOWN` transitions. The backend counter counts  transitions to the whole backend being down, rather than the sum of the counters for each server.
 22. **lastchg** : number of seconds since the last `UP<->DOWN` transition
 23. **downtime** : total downtime (in seconds). The value for the backend is the downtime for the whole backend, not the sum of the server downtime.
 24. **qlimit** : configured maxqueue for the server, or nothing in the value is 0 (default, meaning no limit)
 25. **pid** : process id `(0 for first instance, 1 for second, ...)`
 26. **iid** : unique proxy id
 27. **sid** : server id `(unique inside a proxy)`
 28. **throttle** : current throttle percentage for the server, when slowstart is active, or no value if not in slowstart.
 29. **lbtot** : total number of times a server was selected, either for new sessions, or when re-dispatching. The server counter is the number
     of times that server was selected.
 30. **tracked** : id of proxy/server if tracking is enabled.
 31. **type** : (0=frontend, 1=backend, 2=server, 3=socket/listener)
 32. **rate** : number of sessions per second over last elapsed second
 33. **rate_lim** : configured limit on new sessions per second
 34. **rate_max** : max number of new sessions per second
 35. **check_status** : status of last health check, one of:
 ```
        UNK     -> unknown
        INI     -> initializing
        SOCKERR -> socket error
        L4OK    -> check passed on layer 4, no upper layers testing enabled
        L4TOUT  -> layer 1-4 timeout
        L4CON   -> layer 1-4 connection problem, for example
                   "Connection refused" (tcp rst) or "No route to host" (icmp)
        L6OK    -> check passed on layer 6
        L6TOUT  -> layer 6 (SSL) timeout
        L6RSP   -> layer 6 invalid response - protocol error
        L7OK    -> check passed on layer 7
        L7OKC   -> check conditionally passed on layer 7, for example 404 with
                   disable-on-404
        L7TOUT  -> layer 7 (HTTP/SMTP) timeout
        L7RSP   -> layer 7 invalid response - protocol error
        L7STS   -> layer 7 response error, for example HTTP 5xx
        
     Notice: If a check is currently running, the last known status will be reported, prefixed with "* ".
        e. g. "* L7OK".
```
 36. **check_code** : layer5-7 code, if available
 37. **check_duration** : time in `ms` took to finish last health check
 38. **hrsp_1xx** : http responses with `1xx` code
 39. **hrsp_2xx** : http responses with `2xx` code
 40. **hrsp_3xx** : http responses with `3xx` code
 41. **hrsp_4xx** : http responses with `4xx` code
 42. **hrsp_5xx** : http responses with `5xx` code
 43. **hrsp_other** : http responses with other codes (protocol error)
 44. **hanafail** : failed health checks details
 45. **req_rate** : HTTP requests per second over last elapsed second
 46. **req_rate_max** : max number of HTTP requests per second observed
 47. **req_tot** : total number of HTTP requests received
 48. **cli_abrt** : number of data transfers aborted by the client
 49. **srv_abrt** : number of data transfers aborted by the server (inc. in `eresp`)
 50. **comp_in** : number of HTTP response bytes fed to the compressor
 51. **comp_out** : number of HTTP response bytes emitted by the compressor
 52. **comp_byp** : number of bytes that bypassed the HTTP compressor `(CPU/BW limit)`
 53. **comp_rsp** : number of HTTP responses that were compressed
 54. **lastsess** : number of seconds since last session assigned to server/backend
 55. **last_chk** : last health check contents or textual error
 56. **last_agt** : last agent check contents or textual error
 57. **qtime** : the average queue time in ms over the `1024 last requests`
 58. **ctime** : the average connect time in ms over the `1024 last requests`
 59. **rtime** : the average response time in ms over the `1024 last requests (0 for TCP)`
 60. **ttime** : the average total session time in ms over the `1024 last requests`
 61. **agent_status** : status of last agent check, one of:
 ```
        UNK     -> unknown
        INI     -> initializing
        SOCKERR -> socket error
        L4OK    -> check passed on layer 4, no upper layers testing enabled
        L4TOUT  -> layer 1-4 timeout
        L4CON   -> layer 1-4 connection problem, for example
                   "Connection refused" (tcp rst) or "No route to host" (icmp)
        L7OK    -> agent reported "up"
        L7STS   -> agent reported "fail", "stop", or "down"
```
 62. **agent_code** : numeric code reported by agent if any (unused for now)
 63. **agent_duration** : time in ms taken to finish last check
 64. **check_desc** : short human-readable description of `check_status`
 65. **agent_desc** : short human-readable description of `agent_status`
 66. **check_rise** : server's `rise` parameter used by checks
 67. **check_fall** : server's `fall` parameter used by checks
 68. **check_health** : server's health check value between `0 and rise+fall-1`
 69. **agent_rise** : agent's `rise` parameter, normally 1
 70. **agent_fall** : agent's `fall` parameter, normally 1
 71. **agent_health** : agent's health parameter, between 0 and `rise+fall-1`
 72. **addr** : `address:port` or `unix`. IPv6 has brackets around the address.
 73. **cookie** : server's cookie value or backend's cookie name
 74. **mode** : proxy mode `(tcp, http, health, unknown)`
 75. **algo** : load balancing algorithm
 76. **conn_rate** : number of connections over the last elapsed second
 77. **conn_rate_max** : highest known `conn_rate`
 78. **conn_tot** : cumulative number of connections
 79. **intercepted** : cumulative number of intercepted requests (monitor, stats)
 80. **dcon** : requests denied by `tcp-request connection` rules
 81. **dses** : requests denied by `tcp-request session` rules