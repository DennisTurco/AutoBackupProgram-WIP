package application;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

	public class TreeCopyFileVisitor extends SimpleFileVisitor<Path> {
    private Path source;
    private final Path target;
    private boolean copied = false;
    LoadingAutoBackup loading;
    
    public TreeCopyFileVisitor(String source, String target, int file_number) {
        this.source = Paths.get(source);
        this.target = Paths.get(target);
        this.loading = new LoadingAutoBackup(file_number);     
    }

	@Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path resolve = target.resolve(source.relativize(dir));
        if (Files.notExists(resolve)) {
            Files.createDirectories(resolve);
            try {
            	BufferedWriter bw = new BufferedWriter(new FileWriter("res//log_file", true));
    			bw.write("Create directories : " + resolve);
    			bw.write("\n");
    			bw.close();

            } catch(Exception ex) {
            	System.out.println(ex);
            }
            System.out.println("Create directories : " + resolve);
            copied = true;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {   	
        Path resolve = target.resolve(source.relativize(file));
        Files.copy(file, resolve, StandardCopyOption.REPLACE_EXISTING);
        loading.addLoadingProgression();
        
        try {
        	BufferedWriter bw = new BufferedWriter(new FileWriter("res//log_file", true));
			bw.write("Copy File from \t" + file + "\t to" + resolve);
			bw.write("\n");
			bw.close();
			
        } catch(Exception ex) {
        	System.out.println(ex);
        }
        
        System.out.println(String.format("Copy File from \t'%s' to \t'%s'", file, resolve));
        copied = true;
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
        System.err.format("Unable to copy: %s: %s%n", file, exc);
        copied = false;
        return FileVisitResult.CONTINUE;
    }
    
    public boolean getCopied() {
    	return copied;
    }

}
