FROM debian:bookworm
ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Shanghai

RUN sed -i 's|http://deb.debian.org/debian|http://mirrors.aliyun.com/debian|g' /etc/apt/sources.list.d/debian.sources \
    && sed -i 's|http://security.debian.org/debian-security|http://mirrors.aliyun.com/debian-security|g' /etc/apt/sources.list.d/debian.sources

RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates autoconf libevent-dev bison automake libtool pkg-config build-essential libncurses-dev

RUN git clone https://github.com/tmux/tmux.git && cd tmux && sh autogen.sh && ./configure && make && make install


