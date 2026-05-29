import { Box, Typography, Grid, Link, Divider } from "@mui/material";
import appConfig from "../../config/appConfig";

const Footer = () => (
  <Box component="footer" sx={{ bgcolor: "background.paper", mt: 4, pt: 4, pb: 2 }}>
    <Divider />
    <Box sx={{ maxWidth: 1200, mx: "auto", px: 2, py: 3 }}>
      <Grid container spacing={4}>
        <Grid item xs={12} sm={3}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>
            Get to Know Us
          </Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>About {appConfig.APP_NAME}</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Careers</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Press Releases</Typography>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>Make Money With Us</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Sell on {appConfig.APP_NAME}</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Become an Affiliate</Typography>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>Let Us Help You</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Your Account</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Returns & Replacements</Typography>
          <Typography variant="body2" component={Link} href="#" sx={{ display: "block", mb: 0.5, textDecoration: "none", color: "text.secondary" }}>Help</Typography>
        </Grid>
        <Grid item xs={12} sm={3}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>Contact Us</Typography>
          <Typography variant="body2" color="text.secondary">{appConfig.SUPPORT_EMAIL}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Built with ❤️ for learning microservices
          </Typography>
        </Grid>
      </Grid>
      <Divider sx={{ my: 2 }} />
      <Typography variant="body2" align="center" color="text.secondary">
        © {new Date().getFullYear()} {appConfig.COMPANY_NAME}. All rights reserved. | Demo Application for Learning
      </Typography>
    </Box>
  </Box>
);

export default Footer;
