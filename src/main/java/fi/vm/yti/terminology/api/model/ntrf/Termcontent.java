//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2018.09.18 at 09:30:55 AM EEST 
//


package fi.vm.yti.terminology.api.model.ntrf;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for termcontent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="termcontent">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{}EQUI" minOccurs="0"/>
 *         &lt;element ref="{}TERM"/>
 *         &lt;element ref="{}HOGR" minOccurs="0"/>
 *         &lt;element ref="{}GEOG" minOccurs="0"/>
 *         &lt;element ref="{}TYPT" minOccurs="0"/>
 *         &lt;element ref="{}PHR" minOccurs="0"/>
 *         &lt;element ref="{}PRON" minOccurs="0"/>
 *         &lt;element ref="{}ETYM" minOccurs="0"/>
 *         &lt;element ref="{}SUBJ" minOccurs="0"/>
 *         &lt;element ref="{}SCOPE" minOccurs="0"/>
 *         &lt;element ref="{}SOURF" minOccurs="0"/>
 *         &lt;element ref="{}STAT" minOccurs="0"/>
 *         &lt;element ref="{}ADD" minOccurs="0"/>
 *         &lt;element ref="{}REMK" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "termcontent", propOrder = {
    "equi",
    "term",
    "hogr",
    "geog",
    "typt",
    "phr",
    "pron",
    "etym",
    "subj",
    "scope",
    "sourf",
    "stat",
    "add",
    "remk"
})
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class Termcontent {

    @XmlElement(name = "EQUI")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected EQUI equi;
    @XmlElement(name = "TERM", required = true)
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected TERM term;
    @XmlElement(name = "HOGR")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String hogr;
    @XmlElement(name = "GEOG")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String geog;
    @XmlElement(name = "TYPT")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String typt;
    @XmlElement(name = "PHR")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String phr;
    @XmlElement(name = "PRON")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String pron;
    @XmlElement(name = "ETYM")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String etym;
    @XmlElement(name = "SUBJ")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected SUBJ subj;
    @XmlElement(name = "SCOPE")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected SCOPE scope;
    @XmlElement(name = "SOURF")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected SOURF sourf;
    @XmlElement(name = "STAT")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String stat;
    @XmlElement(name = "ADD")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String add;
    @XmlElement(name = "REMK")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected REMK remk;

    /**
     * Gets the value of the equi property.
     * 
     * @return
     *     possible object is
     *     {@link EQUI }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public EQUI getEQUI() {
        return equi;
    }

    /**
     * Sets the value of the equi property.
     * 
     * @param value
     *     allowed object is
     *     {@link EQUI }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setEQUI(EQUI value) {
        this.equi = value;
    }

    /**
     * Gets the value of the term property.
     * 
     * @return
     *     possible object is
     *     {@link TERM }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public TERM getTERM() {
        return term;
    }

    /**
     * Sets the value of the term property.
     * 
     * @param value
     *     allowed object is
     *     {@link TERM }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setTERM(TERM value) {
        this.term = value;
    }

    /**
     * Gets the value of the hogr property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getHOGR() {
        return hogr;
    }

    /**
     * Sets the value of the hogr property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setHOGR(String value) {
        this.hogr = value;
    }

    /**
     * Gets the value of the geog property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getGEOG() {
        return geog;
    }

    /**
     * Sets the value of the geog property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setGEOG(String value) {
        this.geog = value;
    }

    /**
     * Gets the value of the typt property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getTYPT() {
        return typt;
    }

    /**
     * Sets the value of the typt property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setTYPT(String value) {
        this.typt = value;
    }

    /**
     * Gets the value of the phr property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getPHR() {
        return phr;
    }

    /**
     * Sets the value of the phr property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setPHR(String value) {
        this.phr = value;
    }

    /**
     * Gets the value of the pron property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getPRON() {
        return pron;
    }

    /**
     * Sets the value of the pron property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setPRON(String value) {
        this.pron = value;
    }

    /**
     * Gets the value of the etym property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getETYM() {
        return etym;
    }

    /**
     * Sets the value of the etym property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setETYM(String value) {
        this.etym = value;
    }

    /**
     * Gets the value of the subj property.
     * 
     * @return
     *     possible object is
     *     {@link SUBJ }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public SUBJ getSUBJ() {
        return subj;
    }

    /**
     * Sets the value of the subj property.
     * 
     * @param value
     *     allowed object is
     *     {@link SUBJ }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setSUBJ(SUBJ value) {
        this.subj = value;
    }

    /**
     * Gets the value of the scope property.
     * 
     * @return
     *     possible object is
     *     {@link SCOPE }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public SCOPE getSCOPE() {
        return scope;
    }

    /**
     * Sets the value of the scope property.
     * 
     * @param value
     *     allowed object is
     *     {@link SCOPE }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setSCOPE(SCOPE value) {
        this.scope = value;
    }

    /**
     * Gets the value of the sourf property.
     * 
     * @return
     *     possible object is
     *     {@link SOURF }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public SOURF getSOURF() {
        return sourf;
    }

    /**
     * Sets the value of the sourf property.
     * 
     * @param value
     *     allowed object is
     *     {@link SOURF }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setSOURF(SOURF value) {
        this.sourf = value;
    }

    /**
     * Gets the value of the stat property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getSTAT() {
        return stat;
    }

    /**
     * Sets the value of the stat property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setSTAT(String value) {
        this.stat = value;
    }

    /**
     * Gets the value of the add property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getADD() {
        return add;
    }

    /**
     * Sets the value of the add property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setADD(String value) {
        this.add = value;
    }

    /**
     * Gets the value of the remk property.
     * 
     * @return
     *     possible object is
     *     {@link REMK }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public REMK getREMK() {
        return remk;
    }

    /**
     * Sets the value of the remk property.
     * 
     * @param value
     *     allowed object is
     *     {@link REMK }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-09-18T09:30:55+03:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setREMK(REMK value) {
        this.remk = value;
    }

}