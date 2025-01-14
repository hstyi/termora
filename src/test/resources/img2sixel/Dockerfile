FROM ubuntu
RUN sed -i 's/ports.ubuntu.com/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt update && apt install curl libsixel-bin -y && curl https://kf-ui.cdn-go.cn/weapp-image/latest/weapp-game/kf_pc/home_Artificial_pic1.png -o qq.png

CMD img2sixel qq.png


# docker build -t img2sixel .
# docker run -it --rm img2sixel