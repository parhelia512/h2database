-- Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
-- and the EPL 1.0 (https://h2database.com/html/license.html).
-- Initial Developer: H2 Group
--

select exp(null) vn, left(exp(1), 4) v1, left(exp(1.1), 4) v2, left(exp(-1.1), 4) v3, left(exp(1.9), 4) v4, left(exp(-1.9), 4) v5;
> VN   V1   V2   V3   V4   V5
> ---- ---- ---- ---- ---- ----
> null 2.71 3.00 0.33 6.68 0.14
> rows: 1
