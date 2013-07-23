package com.mysms.jenkins;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.NameValuePair;
import org.junit.Test;

import com.mysms.jenkins.MysmsNotifier;

public class MysmsNotifierTest {

    @Test
    public void testParsingUserList() {
        Map<String,NameValuePair> result = MysmsNotifier.parseUserList("test.user:415 555 5555:Test User,C:D:E,E:F:G");
        assertTrue(result.get("test.user").equals(new NameValuePair("Test User", "415 555 5555")));
        assertTrue(result.get("C").equals(new NameValuePair("E", "D")));
        assertTrue(result.get("E").equals(new NameValuePair("G", "F")));  
    }
    
    @Test
    public void testSubstitution() {
        Map<String,String> subMap = new HashMap<String,String>();
        subMap.put("%CULPRIT%","Gernot");
        subMap.put("%PROJECT%","MysmsNotifier");
        String input = "Dear %CULPRIT%, your project %PROJECT% is failing.";
        String result = MysmsNotifier.substituteAttributes(input, subMap);
        assertEquals(result,"Dear Gernot, your project MysmsNotifier is failing.");
    }
    
    @Test
    public void testCulpritListToString() {
        List<NameValuePair> phoneToCulprit = new ArrayList<NameValuePair>();
        phoneToCulprit.add(new NameValuePair("William", "4155555555"));
        phoneToCulprit.add(new NameValuePair("James", "4155555556"));
        String result = MysmsNotifier.culpritStringFromList(phoneToCulprit);
        assertEquals(result,"William and James");
        
        phoneToCulprit = new ArrayList<NameValuePair>();
        phoneToCulprit.add(new NameValuePair("James", "4155555556"));
        result = MysmsNotifier.culpritStringFromList(phoneToCulprit);
        assertEquals(result,"James");
        
        phoneToCulprit = new ArrayList<NameValuePair>();
        phoneToCulprit.add(new NameValuePair("William", "4155555555"));
        phoneToCulprit.add(new NameValuePair("James", "4155555556"));
        phoneToCulprit.add(new NameValuePair("Luke", "4155555557"));
        result = MysmsNotifier.culpritStringFromList(phoneToCulprit);
        assertEquals(result,"William James and Luke");
    }
}
