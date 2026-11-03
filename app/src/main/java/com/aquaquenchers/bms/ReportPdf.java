package com.aquaquenchers.bms;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.json.JSONObject;

/** Renders an already authorized report snapshot; no reads, writes or calculations. */
final class ReportPdf {
    private ReportPdf() {}
    static byte[] render(JSONObject report) {
        PdfDocument document=new PdfDocument();
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
            Canvas canvas=page.getCanvas();Paint paint=new Paint();paint.setTextSize(14);
            String[] lines={report.optString("station"),"Financial summary",
                    report.optString("start_date")+" to "+report.optString("end_date"),
                    "Generated: "+Rules.timestamp(report.optString("generated_at")),
                    "Revenue: "+Rules.money(report.optLong("revenue_centavos")),
                    "Payments received: "+Rules.money(report.optLong("payments_received_centavos")),
                    "Expenses: "+Rules.money(report.optLong("expenses_centavos")),
                    "Net income: "+Rules.money(report.optLong("net_income_centavos")),
                    "Outstanding balances: "+Rules.money(report.optLong("outstanding_centavos")),
                    "Net income = Payments received - Expenses"};
            int y=60;for(String line:lines){canvas.drawText(line,40,y,paint);y+=32;}
            document.finishPage(page);document.writeTo(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("PDF could not be generated.");}
        finally{document.close();}
    }
}
