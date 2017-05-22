package play.libs.ws;

import java.io.File;

public class FileBody implements WSBody {
   private final File file;

    public FileBody(File file) {
        this.file = file;
    }

    public File file() {
        return file;
    }
}
