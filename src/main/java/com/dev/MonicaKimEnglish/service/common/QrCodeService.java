package com.dev.MonicaKimEnglish.service.common;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
public class QrCodeService {
    public byte[] png(String text, int size) {
        try {
            var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("QR 코드 생성에 실패했습니다.", e);
        }
    }
}
