package pewter;

/**
 * Created by jakob on 02-10-15.
 */
interface ResourceStore {
    java.util.List<Resource> getAllResources();

    Resource resolveResource(String path);
}
