package com.inmobi.grid.usage;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: srikanth.sundarrajan
 * Date: 10/18/11
 */
public class IncidentAnalyzer {

    public static final String FROM = "Nagios-UA2 <nagios@hmon1>|Hadoop HDFS <hdfs@gs1102.red.ua2.inmobi.com>" +
            "|enterprisedb <enterprisedb@db1014.app.ib1.inmobi.com>|enterprisedb <enterprisedb@db1013.app.ib1.inmobi.com>";

    public static void main(String[] args) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        List<String> events = new ArrayList<String>();
        Properties properties = new Properties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.host", "mail.mkhoj.com");
        properties.put("mail.smtp.port", "25");

        Properties patterns = new Properties();

        try {
            patterns.load(IncidentAnalyzer.class.getResourceAsStream("/patterns.properties"));

            Session session = Session.getDefaultInstance(properties, null);
            Store store = session.getStore("pop3");
            store.connect("mail.mkhoj.com", "mkhoj\\bi_incident_scrub", "incident@123");
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            int count = folder.getMessageCount();
            int range = (count > 500 ? 500 : count);
            Message[] msgs = folder.getMessages(count - range + 1, count);
            Calendar yesterday = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            yesterday.set(Calendar.MILLISECOND, 0);
            yesterday.set(Calendar.SECOND, 0);
            yesterday.set(Calendar.MINUTE, 0);
            yesterday.set(Calendar.HOUR, 0);
            yesterday.add(Calendar.DATE, -1);
            Calendar today = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            for (int i = 0; i < range; i++) {
                System.out.println(msgs[i].getSubject());
                Calendar mailDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                mailDate.setTime(msgs[i].getSentDate());
                if (yesterday.before(mailDate) && today.after(mailDate)) {
                    String text = msgs[i].getSubject();
                    for (Map.Entry entry : patterns.entrySet()) {
                        String key = entry.getKey().toString();
                        if (!key.startsWith("pattern.subject")) continue;

                        String context = key.substring(16);
                        if (text.contains(entry.getValue().toString()) &&
                                msgs[i].getFrom()[0].toString().matches(FROM)) {
                            String content = patterns.getProperty(context);
                            String message = "";
                            int pos = -1;
                            if (content.startsWith("subject@")) {
                                message = msgs[i].getSubject();
                                pos = 8;
                            } else if (content.startsWith("message@")) {
                                StringBuffer buff = new StringBuffer();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(msgs[i].getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    buff.append(line).append('\n');
                                }
                                message = buff.toString().replace("JOB ERROR", "\\u0001JOB ERROR");
                                pos = 8;
                            } else if (content.startsWith("time@")) {
                                message = msgs[i].getSentDate().toString();
                                pos = 5;
                            }
                            if (context.equals("missing.file")) {
                                message = message.replaceAll("; :", " ");
                                message = message.replaceAll("; ", ";");
                            }

                            for (String msg : message.split("\\u0001")) {
                                String[] data = msg.split(content.startsWith("time@") ? "!!!" : " ");
                                String[] positions = content.substring(pos).split(",");
                                StringBuffer buff = new StringBuffer();
                                for (String position : positions) {
                                    int offset = Integer.parseInt(position);
                                    if (offset < data.length) {
                                        if (offset < 0 && offset + data.length >= 0) {
                                            buff.append(data[data.length+offset]).append(' ');
                                        } else if (offset > 0) {
                                            buff.append(data[offset]).append(' ');
                                        }
                                    }
                                }
                                String event = context + " ### " + buff + " ### " + format.format(msgs[i].getSentDate()) ;
                                events.add(event);
                                System.out.println(event);
                            }
                        }
                    }
                }
            }
            Collections.sort(events);
            StringBuffer buff = new StringBuffer();
            for (String event : events) {
                buff.append(event).append('\n');
            }
            try{
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress("bi_incident_scrub@inmobi.com"));
                message.setRecipient(Message.RecipientType.TO, new InternetAddress("bi-dev@inmobi.com"));
                message.setSubject("Incident scrub for " + format.format(yesterday.getTime()).substring(0,10));
                message.setSentDate(new Date());

                MimeBodyPart messagePart = new MimeBodyPart();
                messagePart.setText(buff.toString());
                Multipart multipart = new MimeMultipart();

                multipart.addBodyPart(messagePart);
                message.setContent(multipart);
                Transport.send(message);
            }
            catch (MessagingException e) {
                e.printStackTrace();
            }


        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
    }
}
