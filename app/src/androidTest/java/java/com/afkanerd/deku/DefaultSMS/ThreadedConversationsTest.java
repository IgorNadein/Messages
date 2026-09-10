package java.com.afkanerd.deku.DefaultSMS;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.RunWith;
import org.junit.Ignore;

@RunWith(AndroidJUnit4.class)
@Ignore("Empty legacy scaffold; contains no executable regression scenario")
public class ThreadedConversationsTest {

    Context context;

   public ThreadedConversationsTest() {
       context = InstrumentationRegistry.getInstrumentation().getTargetContext();
   }
}
