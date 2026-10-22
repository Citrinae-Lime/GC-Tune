package com.poc.gctune;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class FontCore {
    public static void validateFont(File f) throws Exception {
        if (f.length() < 12 || f.length() > 64L * 1024 * 1024) throw new IOException("Invalid TTF size");
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            if (in.readInt() != 0x00010000) throw new IOException("TrueType .ttf required, not OTF/TTC");
            int tables = in.readUnsignedShort();
            if (tables == 0 || tables > 256 || 12L + 16L * tables > f.length()) throw new IOException("Corrupted TTF table");
            in.skipBytes(6);
            Set<String> tags = new HashSet<>();
            for (int i=0; i<tables; i++) {
                byte[] tag = new byte[4]; in.readFully(tag); tags.add(new String(tag, "US-ASCII"));
                in.readInt(); long offset = Integer.toUnsignedLong(in.readInt()), size = Integer.toUnsignedLong(in.readInt());
                if (offset + size > f.length()) throw new IOException("Corrupted TTF");
            }
            if (!tags.containsAll(Arrays.asList("head", "cmap", "maxp", "glyf", "loca"))) throw new IOException("Missing required TTF tables");
        }
    }
    public static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] b = new byte[32768]; long n=0; int r;
        while ((r=in.read(b)) != -1) { n+=r; if(n>limit) throw new IOException("File too large"); out.write(b,0,r); }
    }
    public static void patch(File donor, File font, File output) throws Exception {
        validateFont(font);
        try (ZipFile zip = new ZipFile(donor)) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            List<ZipEntry> fonts = new ArrayList<>(); Set<String> names = new HashSet<>();
            for (ZipEntry e : entries) {
                String n=e.getName();
                if (!names.add(n) || n.startsWith("/") || n.contains("\\") || Arrays.asList(n.split("/")).contains("..")) throw new IOException("Invalid archive");
                if (!e.isDirectory() && n.startsWith("fonts/") && n.toLowerCase(Locale.ROOT).endsWith(".ttf")) fonts.add(e);
            }
            if (fonts.size()!=1) throw new IOException("Package must contain exactly one fonts/*.ttf");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output))) {
                long total=0;
                for (ZipEntry e : entries) {
                    ZipEntry target=new ZipEntry(e.getName()); target.setTime(e.getTime());
                    out.putNextEntry(target);
                    try (InputStream in=e==fonts.get(0)?new FileInputStream(font):zip.getInputStream(e)) {
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); copy(in,bytes,64L*1024*1024);
                        total+=bytes.size(); if(total>128L*1024*1024) throw new IOException("Archive too large");
                        bytes.writeTo(out);
                    }
                    out.closeEntry();
                }
            }
        }
    }
    public static String md5(File f) throws Exception {
        MessageDigest d=MessageDigest.getInstance("MD5");
        try (InputStream in=new FileInputStream(f)) { byte[] b=new byte[32768]; int r; while((r=in.read(b))!=-1) d.update(b,0,r); }
        StringBuilder s=new StringBuilder(); for(byte b:d.digest()) s.append(String.format(Locale.ROOT,"%02x",b&255)); return s.toString();
    }
    public static String quote(String s) { return "'"+s.replace("'", "'\"'\"'")+"'"; }
    public static String command(String donor, String archive, String font, String archiveHash, String fontHash, String token) {
        if (!donor.matches("[\\p{L}\\p{N} _.-]{1,100}") || donor.equals(".") || donor.equals("..")) throw new IllegalArgumentException("Invalid font name");
        if (!token.matches("[a-zA-Z0-9-]+")) throw new IllegalArgumentException("token");
        String root="/data/bbkcore/theme/.dwd/c/o/m/b/b/k/t/h/e/m/e/F/";
        return "( S="+quote(archive)+"; R="+quote(font)+"; T="+quote(root+donor+".itz")+"; V="+quote("/data/vfonts/"+donor+".ttf")+"; "
          +"hash(){ md5sum \"$1\" | cut -d' ' -f1; }; "
          +"[ -r \"$S\" ] && [ -r \"$R\" ] || { echo FONT_SRC_UNREADABLE; exit 1; }; "
          +"[ \"$(hash \"$S\")\" = "+quote(archiveHash)+" ] && [ \"$(hash \"$R\")\" = "+quote(fontHash)+" ] || { echo FONT_SRC_STALE; exit 1; }; "
          +"[ -f \"$T\" ] && [ -f \"$V\" ] && [ ! -L \"$T\" ] && [ ! -L \"$V\" ] || { echo FONT_DONOR_MISSING; exit 1; }; "
          +"B=\"$T.gctune-"+token+".bak\"; C=\"$V.gctune-"+token+".bak\"; "
          +"[ ! -e \"$B\" ] && [ ! -e \"$C\" ] || { echo FONT_BACKUP_EXISTS; exit 1; }; "
          +"cp -p \"$T\" \"$B\" && cp -p \"$V\" \"$C\" || { echo FONT_BACKUP_FAILED; exit 1; }; "
          +"[ \"$(hash \"$T\")\" = \"$(hash \"$B\")\" ] && [ \"$(hash \"$V\")\" = \"$(hash \"$C\")\" ] || { echo FONT_BACKUP_FAILED; exit 1; }; "
          +"am force-stop com.bbk.theme; "
          +"if cat \"$S\" > \"$T\" && cat \"$R\" > \"$V\" && [ \"$(hash \"$T\")\" = "+quote(archiveHash)+" ] && [ \"$(hash \"$V\")\" = "+quote(fontHash)+" ]; then "
          +"echo FONT_INSTALLED; echo BACKUP_ITZ=\"$B\"; echo BACKUP_TTF=\"$C\"; "
          +"else cat \"$B\" > \"$T\"; cat \"$C\" > \"$V\"; if [ \"$(hash \"$T\")\" = \"$(hash \"$B\")\" ] && [ \"$(hash \"$V\")\" = \"$(hash \"$C\")\" ]; then echo FONT_FAILED_ROLLED_BACK; else echo FONT_FAILED_RESTORE_REQUIRED; fi; exit 1; fi; "
          +"ACT=$(cmd package resolve-activity --brief -c android.intent.category.LAUNCHER com.bbk.theme 2>/dev/null | tail -n1); "
          +"am start -n \"$ACT\"; echo APPLY_DONOR_IN_ITHEME_THEN_REBOOT; )";
    }
}
