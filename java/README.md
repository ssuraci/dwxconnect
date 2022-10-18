# DWX_Connect - Introduction

DWX_Connect provides functions to subscribe to tick and bar data, as well as to trade on MT4 or MT5 via python, java and C#. Its simple file-based communication also provides an easy starting point for implementations in other programming languages.

# Java Refactoring

In comparison to original DWX Connect Java implementation (available [here](https://github.com/darwinex/dwxconnect/tree/main/java)), I implemented the following features/improvements:

1. ported project to a maven multimodule build system
2. implemented logging with slf4j / logback
3. implemented start/stop methods for DwxClient
4. unified file polling in DwxClient, reducing code duplication
5. code improvements and fixes for some possible race conditions
6. added a `correlationId` for each command / response message that can be used to implement request/response semantics (for this I had to modify mql5 file as well, anyway I kept it backward compatibile)
7. implemented a very simple "MetaTrader Simulator" that can be used to run unit tests without the need of "real" MetaTrader (which can be dangerous)

# Building

To build the library and sample client:

`mvn clean package`

To run sample client:

`cd dwx-sample-client/target`

`java -jar sample-client-0.1.0.jar path-to-mt5-dir`

