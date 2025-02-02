FROM linuxserver/openssh-server
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories \
    && apk update && apk add wget gcc g++ git make zsh htop xauth openbox xclock xcalc&& wget https://ohse.de/uwe/releases/lrzsz-0.12.20.tar.gz \
    && tar -xf lrzsz-0.12.20.tar.gz && cd lrzsz-0.12.20 && ./configure && make && make install \
    && ln -s /usr/local/bin/lrz /usr/local/bin/rz && ln -s /usr/local/bin/lsz /usr/local/bin/sz

