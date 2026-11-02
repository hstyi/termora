#pragma once

#include <QProxyStyle>

class ApplicationStyle : public QProxyStyle {
    Q_OBJECT

public:
    explicit ApplicationStyle(QStyle *style = nullptr);

    ~ApplicationStyle() override;

    int styleHint(StyleHint stylehint, const QStyleOption *opt, const QWidget *widget,
                  QStyleHintReturn *returnData) const override;

    void drawPrimitive(PrimitiveElement pe, const QStyleOption *opt, QPainter *p, const QWidget *w) const override;

    int pixelMetric(PixelMetric metric, const QStyleOption *option, const QWidget *widget) const override;
};
