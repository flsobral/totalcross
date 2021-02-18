# Copyright (C) 2021 TotalCross Global Mobile Platform Ltda.
#
# SPDX-License-Identifier: LGPL-2.1-only

cmake_minimum_required(VERSION 2.8.2)
# cmake policy required to use project property VERSION
cmake_policy(SET CMP0048 NEW)

include(ExternalProject)

project(SQLite3
    VERSION 3.13.0.0
    LANGUAGES C
)
set(${PROJECT_NAME}_YEAR 2016)
# property <PROJECT>_HOMEPAGE_URL requires cmake 3.12
# using a variable instead we can keep greater compatibility
set(${PROJECT_NAME}_HOMEPAGE_URL https://sqlite.org)

# Place a dummy source at configuration time
if(NOT EXISTS "${CMAKE_CURRENT_BINARY_DIR}/src/sqlite-see.c")
    configure_file(sqlite3_tc.h src/sqlite-see.c)
endif()

ExternalProject_Add(SQLite3Download
    URL $ENV{URL_SEE_31300}
    HTTP_USERNAME $ENV{URL_SEE_USER}
    HTTP_PASSWORD $ENV{URL_SEE_PASS}
    SOURCE_DIR "${CMAKE_CURRENT_BINARY_DIR}/src"
    UPDATE_COMMAND ""
    PATCH_COMMAND patch -p0 <  ${CMAKE_CURRENT_LIST_DIR}/sqlite3tc.patch && cat sqlite3.c see.c > sqlite-see.c
    CONFIGURE_COMMAND ""
    BUILD_COMMAND ""
    INSTALL_COMMAND ""
    TEST_COMMAND ""
)

# using INTERFACE no compilation is performed
add_library(SQLite3 INTERFACE)

# make sure SQLite3Download is executed first
add_dependencies(SQLite3 SQLite3Download)

target_include_directories(SQLite3 INTERFACE 
    "${CMAKE_CURRENT_BINARY_DIR}/src"
    ${CMAKE_CURRENT_LIST_DIR}
)
target_sources(SQLite3 INTERFACE 
    "${CMAKE_CURRENT_BINARY_DIR}/src/sqlite-see.c"
)
