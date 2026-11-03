package com.aquaquenchers.bms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.OutputStream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic local artifact test, not a saved business report or real Auth exercise. */
@RunWith(AndroidJUnit4.class)
public class ReportPdfDeviceTest {
    @Test public void syntheticReportProducesRenderablePdf() throws Exception {
        JSONObject fixture=new JSONObject("{\"station\":\"Aqua Quenchers\",\"start_date\":\"2026-10-01\",\"end_date\":\"2026-10-15\",\"generated_at\":\"2026-10-15T02:30:00Z\",\"revenue_centavos\":28000,\"payments_received_centavos\":0,\"expenses_centavos\":75000,\"net_income_centavos\":-75000,\"outstanding_centavos\":28000}");
        byte[] bytes=ReportPdf.render(fixture);
        assertTrue(bytes.length>1000);assertEquals('%',bytes[0]);assertEquals('P',bytes[1]);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(OutputStream out=context.openFileOutput("synthetic-report.pdf",Context.MODE_PRIVATE)){out.write(bytes);}
        File file=new File(context.getFilesDir(),"synthetic-report.pdf");
        try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer=new PdfRenderer(descriptor)){
            assertEquals(1,renderer.getPageCount());
            try(PdfRenderer.Page page=renderer.openPage(0)){
                assertEquals(595,page.getWidth());assertEquals(842,page.getHeight());
                Bitmap bitmap=Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888);
                try{page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}
                finally{bitmap.recycle();}
            }
        }
    }
}
