package eu.socialsensor.main;


import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javafx.beans.binding.IntegerBinding;
import lombok.Getter;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.math3.util.CombinatoricsUtils;

import com.google.common.primitives.Ints;
import com.thinkaurelius.titan.graphdb.configuration.GraphDatabaseConfiguration;

import eu.socialsensor.dataset.DatasetFactory;


/**
 * @author Alexander Patrikalakis
 */
public class BenchmarkConfiguration {

    // OrientDB Configuration
    private static final String LIGHTWEIGHT_EDGES = "lightweight-edges";

    // Sparksee / DEX configuration
    private static final String LICENSE_KEY = "license-key";

    // Titan specific configuration
    private static final String TITAN = "titan";
    private static final String BUFFER_SIZE = GraphDatabaseConfiguration.BUFFER_SIZE.getName();
    private static final String IDS_BLOCKSIZE = GraphDatabaseConfiguration.IDS_BLOCK_SIZE.getName();
    private static final String PAGE_SIZE = GraphDatabaseConfiguration.PAGE_SIZE.getName();
    public static final String CSV_INTERVAL = GraphDatabaseConfiguration.METRICS_CSV_INTERVAL.getName();
    public static final String CSV = GraphDatabaseConfiguration.METRICS_CSV_NS.getName();
    private static final String CSV_DIR = GraphDatabaseConfiguration.METRICS_CSV_DIR.getName();
    public static final String GRAPHITE = GraphDatabaseConfiguration.METRICS_GRAPHITE_NS.getName();
    private static final String GRAPHITE_HOSTNAME = GraphDatabaseConfiguration.GRAPHITE_HOST.getName();

    // benchmark configuration
    private static final String DATASET = "dataset";
    private static final String DATABASE_STORAGE_DIRECTORY = "database-storage-directory";
    private static final String ACTUAL_COMMUNITIES = "actual-communities";
    private static final String NODES_COUNT = "nodes-count";
    private static final String RANDOMIZE_CLUSTERING = "randomize-clustering";
    private static final String CACHE_VALUES = "cache-values";
    private static final String CACHE_INCREMENT_FACTOR = "cache-increment-factor";
    private static final String CACHE_VALUES_COUNT = "cache-values-count";
    private static final String PERMUTE_BENCHMARKS = "permute-benchmarks";
    private static final String RANDOM_NODES = "shortest-path-random-nodes";

    private static final Set<String> metricsReporters = new HashSet<>();


    static {
        metricsReporters.add( CSV );
        metricsReporters.add( GRAPHITE );
    }


    @Getter private final File dataset;
    @Getter private final List<BenchmarkType> benchmarkTypes;
    @Getter private final SortedSet<GraphDatabaseType> selectedDatabases;
    @Getter private final File resultsPath;

    // storage directory
    @Getter private final File dbStorageDirectory;

    // metrics (optional)
    @Getter private final long csvReportingInterval; // Titan:  "Time between dumps of CSV files containing Metrics data, in milliseconds"
    @Getter private final File csvDir; // Titan
    @Getter private final String graphiteHostname; // Titan
    @Getter private final long graphiteReportingInterval; // Titan

    // storage backend specific settings
    @Getter private final Boolean orientLightweightEdges; // Orient
    @Getter private final String sparkseeLicenseKey;  // Sparksee

    // shortest path
    @Getter private final int randomNodes;

    // clustering
    @Getter private final Boolean randomizedClustering;
    @Getter private final Integer nodesCount;
    @Getter private final Integer cacheValuesCount;
    @Getter private final Double cacheIncrementFactor;
    @Getter private final List<Integer> cacheValues;
    @Getter private final File actualCommunities;
    @Getter private final boolean permuteBenchmarks;
    @Getter private final int scenarios;
    @Getter private final int titanBufferSize; // Titan
    @Getter private final int titanIdsBlocksize; // Titan
    @Getter private final int titanPageSize; // Titan



    public BenchmarkConfiguration( Configuration appconfig ) {
        if ( appconfig == null ) {
            throw new IllegalArgumentException( "appconfig may not be null" );
        }

        Configuration eu = appconfig.subset( "eu" );
        Configuration socialsensor = eu.subset( "socialsensor" );

        //metrics
        final Configuration metrics = socialsensor.subset( GraphDatabaseConfiguration.METRICS_NS.getName() );

        final Configuration graphite = metrics.subset( GRAPHITE );
        this.graphiteHostname = graphite.getString( GRAPHITE_HOSTNAME, null );
        this.graphiteReportingInterval = graphite.getLong( GraphDatabaseConfiguration.GRAPHITE_INTERVAL.getName(), 1000 /*default 1sec*/ );

        final Configuration csv = metrics.subset( CSV );
        this.csvReportingInterval = metrics.getLong( CSV_INTERVAL, 1000 /*ms*/ );
        this.csvDir = csv.containsKey( CSV_DIR ) ? new File( csv.getString( CSV_DIR, System.getProperty( "user.dir" ) /*default*/ ) ) : null;

        Configuration orient = socialsensor.subset( "orient" );
        orientLightweightEdges = orient.containsKey( LIGHTWEIGHT_EDGES ) ? orient.getBoolean( LIGHTWEIGHT_EDGES ) : null;

        Configuration sparksee = socialsensor.subset( "sparksee" );
        sparkseeLicenseKey = sparksee.containsKey( LICENSE_KEY ) ? sparksee.getString( LICENSE_KEY ) : null;

        Configuration titan = socialsensor.subset( TITAN ); //TODO(amcp) move DynamoDB ns into titan
        titanBufferSize = titan.getInt( BUFFER_SIZE, GraphDatabaseConfiguration.BUFFER_SIZE.getDefaultValue() );
        titanIdsBlocksize = titan.getInt( IDS_BLOCKSIZE, GraphDatabaseConfiguration.IDS_BLOCK_SIZE.getDefaultValue() );
        titanPageSize = titan.getInt( PAGE_SIZE, GraphDatabaseConfiguration.PAGE_SIZE.getDefaultValue() );

        // database storage directory
        if ( !socialsensor.containsKey( DATABASE_STORAGE_DIRECTORY ) ) {
            throw new IllegalArgumentException( "configuration must specify database-storage-directory" );
        }
        dbStorageDirectory = new File( socialsensor.getString( DATABASE_STORAGE_DIRECTORY ) );
        dataset = validateReadableFile( socialsensor.getString( DATASET ), DATASET );

        // load the dataset
        DatasetFactory.getInstance().getDataset( dataset );

        if ( !socialsensor.containsKey( PERMUTE_BENCHMARKS ) ) {
            throw new IllegalArgumentException( "configuration must set permute-benchmarks to true or false" );
        }
        permuteBenchmarks = socialsensor.getBoolean( PERMUTE_BENCHMARKS );

        List<?> benchmarkList = socialsensor.getList( "benchmarks" );
        benchmarkTypes = new ArrayList<>();
        for ( Object str : benchmarkList ) {
            benchmarkTypes.add( BenchmarkType.valueOf( str.toString() ) );
        }

        selectedDatabases = new TreeSet<>();
        for ( Object database : socialsensor.getList( "databases" ) ) {
            if ( !GraphDatabaseType.STRING_REP_MAP.keySet().contains( database.toString() ) ) {
                throw new IllegalArgumentException( String.format( "selected database %s not supported", database.toString() ) );
            }
            selectedDatabases.add( GraphDatabaseType.STRING_REP_MAP.get( database ) );
        }
        scenarios = permuteBenchmarks ? Ints.checkedCast( CombinatoricsUtils.factorial( selectedDatabases.size() ) ) : 1;

        resultsPath = new File( System.getProperty( "user.dir" ), socialsensor.getString( "results-path" ) );
        if ( !resultsPath.exists() && !resultsPath.mkdirs() ) {
            throw new IllegalArgumentException( "unable to create results directory" );
        }
        if ( !resultsPath.canWrite() ) {
            throw new IllegalArgumentException( "unable to write to results directory" );
        }

        randomNodes = socialsensor.getInteger( RANDOM_NODES, 100 );

        if ( this.benchmarkTypes.contains( BenchmarkType.CLUSTERING ) ) {
            if ( !socialsensor.containsKey( NODES_COUNT ) ) {
                throw new IllegalArgumentException( "the CW benchmark requires nodes-count integer in config" );
            }
            nodesCount = socialsensor.getInt( NODES_COUNT );

            if ( !socialsensor.containsKey( RANDOMIZE_CLUSTERING ) ) {
                throw new IllegalArgumentException( "the CW benchmark requires randomize-clustering bool in config" );
            }
            randomizedClustering = socialsensor.getBoolean( RANDOMIZE_CLUSTERING );

            if ( !socialsensor.containsKey( ACTUAL_COMMUNITIES ) ) {
                throw new IllegalArgumentException( "the CW benchmark requires a file with actual communities" );
            }
            actualCommunities = validateReadableFile( socialsensor.getString( ACTUAL_COMMUNITIES ), ACTUAL_COMMUNITIES );

            final boolean notGenerating = socialsensor.containsKey( CACHE_VALUES );
            if ( notGenerating ) {
                List<?> objects = socialsensor.getList( CACHE_VALUES );
                cacheValues = new ArrayList<>( objects.size() );
                cacheValuesCount = null;
                cacheIncrementFactor = null;
                for ( Object o : objects ) {
                    cacheValues.add( Integer.valueOf( o.toString() ) );
                }
            } else if ( socialsensor.containsKey( CACHE_VALUES_COUNT ) && socialsensor.containsKey( CACHE_INCREMENT_FACTOR ) ) {
                cacheValues = null;
                // generate the cache values with parameters
                if ( !socialsensor.containsKey( CACHE_VALUES_COUNT ) ) {
                    throw new IllegalArgumentException( "the CW benchmark requires cache-values-count int in config when cache-values not specified" );
                }
                cacheValuesCount = socialsensor.getInt( CACHE_VALUES_COUNT );

                if ( !socialsensor.containsKey( CACHE_INCREMENT_FACTOR ) ) {
                    throw new IllegalArgumentException( "the CW benchmark requires cache-increment-factor int in config when cache-values not specified" );
                }
                cacheIncrementFactor = socialsensor.getDouble( CACHE_INCREMENT_FACTOR );
            } else {
                throw new IllegalArgumentException( "when doing CW benchmark, must provide cache-values or parameters to generate them" );
            }
        } else {
            randomizedClustering = null;
            nodesCount = null;
            cacheValuesCount = null;
            cacheIncrementFactor = null;
            cacheValues = null;
            actualCommunities = null;
        }
    }


    public BenchmarkConfiguration( Map<String, String> settings ) {

        // ---- Static values ----
        // Database dir
        dbStorageDirectory = new File( "storage" );

        // Results
        resultsPath = new File( System.getProperty( "user.dir" ), "results" );
        if ( !resultsPath.exists() && !resultsPath.mkdirs() ) {
            throw new IllegalArgumentException( "unable to create results directory" );
        }
        if ( !resultsPath.canWrite() ) {
            throw new IllegalArgumentException( "unable to write to results directory" );
        }



        // ---- Settings from Chronos ----

        // Benchmark Types
        benchmarkTypes = new ArrayList<>();
        benchmarkTypes.add( BenchmarkType.valueOf( settings.get( "benchmark" ) ) );

        // Dataset
        dataset = validateReadableFile( settings.get( settings.get( "dataset" ) ), DATASET );
        DatasetFactory.getInstance().getDataset( dataset );

        // benchmark configuration
        permuteBenchmarks = Boolean.parseBoolean( settings.get( "permuteBenchmark" ) );
        randomNodes = Integer.parseInt( settings.get( "shortestPathRandomNodes" ) );

        if ( this.benchmarkTypes.contains( BenchmarkType.CLUSTERING ) ) {
            randomizedClustering = Boolean.parseBoolean( settings.get("randomizeClustering" ) );
            nodesCount = Integer.parseInt( settings.get("nodesCount" ) );
            actualCommunities = validateReadableFile( settings.get( "actualCommunities" ), ACTUAL_COMMUNITIES );

            final boolean notGenerating = settings.containsKey("cacheValue" );
            if ( notGenerating ) {
                cacheValues = new ArrayList<>( 1 );
                cacheValues.add( Integer.parseInt( settings.get("cacheValue" ) ) );
                cacheValuesCount = null;
                cacheIncrementFactor = null;
            } else if ( settings.containsKey("cacheValuesCount") && settings.containsKey("cacheIncrementFactor") ) {
                cacheValues = null;
                cacheValuesCount =  Integer.parseInt( settings.get("cacheValuesCount") );
                cacheIncrementFactor = Double.parseDouble( settings.get("cacheIncrementFactor") );
            } else {
                throw new IllegalArgumentException( "when doing CW benchmark, must provide cache-values or parameters to generate them" );
            }
        } else {
            randomizedClustering = null;
            nodesCount = null;
            cacheValuesCount = null;
            cacheIncrementFactor = null;
            cacheValues = null;
            actualCommunities = null;
        }

        // Database System
        if ( !GraphDatabaseType.STRING_REP_MAP.keySet().contains( settings.get( "system") ) ) {
            throw new IllegalArgumentException( String.format( "selected database %s not supported", settings.get( "system") ) );
        }
        selectedDatabases = new TreeSet<>();
        selectedDatabases.add( GraphDatabaseType.STRING_REP_MAP.get( settings.get( "system") ) );
        scenarios = permuteBenchmarks ? Ints.checkedCast( CombinatoricsUtils.factorial( selectedDatabases.size() ) ) : 1;

        // Metrics
        this.csvReportingInterval = Long.parseLong( settings.get("titan.csvReportingInterval"));
        this.csvDir = new File( settings.get( "titan.metrics" ) );
        this.graphiteHostname = settings.get( "titan.hostname" );
        this.graphiteReportingInterval = Long.parseLong( settings.get( "titan.reportingInterval" ) );

        // Orient
        orientLightweightEdges = Boolean.parseBoolean( settings.get( "orient.lightweightEdges" ) );

        // Sparksee
        sparkseeLicenseKey = settings.get("sparksee.licenseKey");

        // Clustering
        titanBufferSize = Integer.parseInt( settings.get( "titan.bufferSize" ) );
        titanIdsBlocksize = Integer.parseInt( settings.get( "titan.blockSize" ) );
        titanPageSize = Integer.parseInt( settings.get( "titan.pageSize" ) );
    }


    private static File validateReadableFile( String fileName, String fileType ) {
        File file = new File( fileName );
        if ( !file.exists() ) {
            throw new IllegalArgumentException( String.format( "the %s does not exist", fileType ) );
        }

        if ( !(file.isFile() && file.canRead()) ) {
            throw new IllegalArgumentException( String.format( "the %s must be a file that this user can read", fileType ) );
        }
        return file;
    }



    public boolean publishCsvMetrics() {
        return csvDir != null;
    }


    public boolean publishGraphiteMetrics() {
        return graphiteHostname != null && !graphiteHostname.isEmpty();
    }
}
