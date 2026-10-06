/**
 * Local-file decoding of GOES-19 ABI CONUS clear-sky masks and cloud-top heights.
 * <p>Sources: <a href="https://docs.unidata.ucar.edu/netcdf-java/current/userguide/netcdf_dataset.html">NetCDF-Java</a>,
 * <a href="https://www.star.nesdis.noaa.gov/atmospheric-composition-training/satellite_data_goes_imager_projection.php">NOAA navigation</a>,
 * <a href="https://www.ncei.noaa.gov/sites/default/files/2024-12/GOES-19_ABI_L2_CSM_Beta_ReadMe.pdf">NOAA cloud-mask flags</a>,
 * and <a href="https://www.ncei.noaa.gov/sites/default/files/2024-12/GOES-19_ABI_L2_Cloud_Top_Parameters_Beta_ReadMe.pdf">NOAA cloud-top parameters</a>.
 * <p>AI assistance: OpenAI Codex assisted with parser development and cloud-top height support.
 */
package org.skyview.goes;
