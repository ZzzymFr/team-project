package workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import workspace.domain.*;
import workspace.data_access.FileCanvasRepository;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FileCanvasRepositoryTest {
    @TempDir Path temp;
    private CanvasState fixture() throws IOException {
        var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(2,3,0xffe13942);
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        var ink=new CanvasState.Ink(List.of(new CanvasState.Point(-30,10,123),new CanvasState.Point(400,90,124)),0xff1357fa);
        var formula=new CanvasState.Formula(List.of(0),"x=3",false,50,40,80,22,16,0xff101010);
        var asset=new CanvasState.Asset("y=x","",bytes.toByteArray(),-50,800,200,100);
        return new CanvasState(List.of(ink),List.of(formula),List.of(asset),1.6,-72,33);
    }
    @Test void savesAndReloadsCoordinatesColorsAnswersAndMathSources() throws IOException {
        var repository=new FileCanvasRepository(temp);
        var original=new SavedCanvas(UUID.randomUUID().toString(),"Analysis lecture",Instant.parse("2026-11-12T10:00:00Z"),fixture());
        repository.write(original);
        var loaded=repository.find(original.id()).orElseThrow();
        assertEquals("Analysis lecture",loaded.name());
        assertEquals(-30,loaded.state().ink().get(0).points().get(0).x());
        assertEquals(0xff1357fa,loaded.state().ink().get(0).argb());
        assertEquals("x=3",loaded.state().formulas().get(0).answer());
        assertEquals("y=x",loaded.state().assets().get(0).latex());
        assertEquals(-72,loaded.state().offsetX());
        assertEquals(0xffe13942,ImageIO.read(new ByteArrayInputStream(loaded.state().assets().get(0).png())).getRGB(2,3));
    }
    @Test void saveReplacesSameIdRatherThanCreatingDuplicate() throws IOException {
        var repo=new FileCanvasRepository(temp);String id=UUID.randomUUID().toString();
        repo.write(new SavedCanvas(id,"First",Instant.now(),fixture()));
        repo.write(new SavedCanvas(id,"Renamed",Instant.now(),fixture()));
        assertEquals(1,repo.list().size());assertEquals("Renamed",repo.find(id).orElseThrow().name());
    }
    @Test void oneCorruptedDocumentDoesNotHideOtherSaves() throws IOException {
        var repo=new FileCanvasRepository(temp);String id=UUID.randomUUID().toString();
        repo.write(new SavedCanvas(id,"Good",Instant.now(),fixture()));
        Files.writeString(temp.resolve(UUID.randomUUID()+".mlcanvas"),"not a document");
        assertEquals(1,repo.list().size());
        assertEquals(id,repo.list().get(0).id());
    }
    @Test void imageBytesCannotBeMutatedThroughDomainAccessor() throws IOException {
        var data=fixture().assets().get(0);
        byte[] original=data.png();original[0]=0;
        assertNotEquals(0,data.png()[0]);
    }
    @Test void invalidDocumentIdCannotEscapeStorageFolder() {
        var repo=new FileCanvasRepository(temp);
        assertThrows(IllegalArgumentException.class,()->repo.find("../../other"));
    }
}
