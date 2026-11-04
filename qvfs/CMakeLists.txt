cmake_minimum_required(VERSION 3.25)
project(qvfs)

find_package(
    Qt${QT_MAJOR}
    COMPONENTS Core
    REQUIRED
)

add_library(
    ${PROJECT_NAME}
    include/qvfs/qvirtualfileinfo.h
    include/qvfs/qvirtualjob.h
    include/qvfs/qvirtualfile.h
    include/qvfs/qvirtualfilesystem.h
    include/qvfs/qlocalfilesystem.h
    include/qvfs/qvirtualfileoptions.h
    include/qvfs/qvirtualfilehandle.h
    include/qvfs/qvirtualtransferjob.h
    include/qvfs/private/qvirtualbackend_p.h
    src/qvirtualjob.cpp
    src/qvirtualfile.cpp
    src/qvirtualfilesystem.cpp
    src/qlocalfilesystem.cpp
    src/qlocalfilebackend.cpp
    src/qvirtualbackend.cpp
    src/qvirtualfilehandle.cpp
    src/qvirtualtransferjob.cpp
)
target_include_directories(${PROJECT_NAME} PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_link_libraries(${PROJECT_NAME} PUBLIC Qt${QT_MAJOR}::Core)

if(BUILD_TESTING)
    add_subdirectory(tests)
endif()
