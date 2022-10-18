# DWX_Connect - Introduction

DWX_Connect provides functions to subscribe to tick and bar data, as well as to trade on MT4 or MT5 via python, java and C#. Its simple file-based communication also provides an easy starting point for implementations in other programming languages.

# Java Refactoring (WIP)

Compared to the original DWX Connect Java implementation (available [here](https://github.com/darwinex/dwxconnect/tree/main/java)), I added the following features/improvements:

1. ported project to maven multimodule build system
2. implemented logging with slf4j / logback
3. implemented start/stop methods for DwxClient
4. unified file polling in DwxClient, reducing code duplication
5. code improvements, fixes for some possible race conditions
6. added a `correlationId` for each command / response message that can be used to implement request/reply semantics (for this I had to modify mql5 file as well, anyway I kept it backward compatibile)
7. implemented a very basic "MetaTrader Simulator" that can be used to run unit tests without the need of "real" MetaTrader (which can be dangerous)

# Building

To build the library and sample client:

`mvn clean package`

To run sample client:

`cd dwx-sample-client/target`

`java -jar sample-client-0.1.0.jar path-to-mt5-dir`

# Future Work
- more tests
- Spring Boot sample application 
- containerized application that exposes REST / Kafka / RabbitMQ endpoints
- code optimization, performance improvements
- file based messaging improvement
- better error handling
- align other languages implementation (C#, Python)